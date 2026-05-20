package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Group
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.*
import th.ac.bodin2.electives.proto.api.GroupType

class GroupServiceImpl : GroupService {
    companion object {
        private const val PAGE_SIZE = 50
    }

    @Transactional
    override fun create(
        id: Int,
        name: String,
        type: GroupType,
        parentId: Int?,
    ) = transaction {
        parentId?.let {
            val parent = Group.findById(it) ?: throw EntityNotFoundException(ExceptionEntity.GROUP)
            if (parent.parentId != null) throw ConflictException(ExceptionEntity.GROUP)
        }

        val stmt = Groups.insertIgnore {
            it[this.id] = id
            it[this.name] = name
            it[this.type] = type.value
            it[this.parentId] = parentId
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.GROUP)
        Group.wrapRow(stmt.resultedValues!!.first())
    }

    @Transactional
    override fun delete(id: Int) {
        transaction {
            val type = Group.getType(id) ?: throw EntityNotFoundException(ExceptionEntity.GROUP)
            // GRADE/CLASS groups must never lose members silently
            // Refuse to delete a non-CUSTOM/PROGRAM group that still has members
            if (type != GroupType.CUSTOM.value && type != GroupType.PROGRAM.value) {
                val hasMembers = StudentGroups.selectAll()
                    .where { StudentGroups.group eq id }
                    .empty().not()
                if (hasMembers) throw ConflictException(ExceptionEntity.GROUP)
            }

            val rows = Groups.deleteWhere { Groups.id eq id }
            if (rows == 0) {
                throw EntityNotFoundException(ExceptionEntity.GROUP)
            }
        }
    }

    @Transactional
    override fun update(id: Int, update: GroupService.GroupUpdate) = transaction {
        Group.findById(id) ?: throw EntityNotFoundException(ExceptionEntity.GROUP)
        val rows = Groups.updateReturning(where = { Groups.id eq id }) {
            update.name?.let { name -> it[this.name] = name }
            if (update.setParentId) {
                update.parentId?.let { pid ->
                    val parent = Group.findById(pid) ?: throw EntityNotFoundException(ExceptionEntity.GROUP)
                    if (parent.parentId != null) throw ConflictException(ExceptionEntity.GROUP)
                }
                // A group that already has children cannot become a child itself
                if (update.parentId != null) {
                    val hasChildren = Groups.selectAll().where { Groups.parentId eq id }.empty().not()
                    if (hasChildren) throw ConflictException(ExceptionEntity.GROUP)
                }
                it[this.parentId] = update.parentId
            }

            if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
        }
        Group.wrapRow(rows.first())
    }

    override fun getAll() = Group.all().toList()

    override fun getById(groupId: Int) = Group.findById(groupId)

    @Transactional
    override fun getMembers(groupId: Int, page: Int, query: String?): Pair<List<Student>, Long> = transaction {
        Group.assertExists(groupId)

        val searchCondition = query?.takeIf { it.isNotBlank() }?.let { userSearchCondition(it) }

        val baseJoin = (StudentGroups innerJoin Students innerJoin Users)
        val groupFilter: Op<Boolean> = StudentGroups.group eq groupId
        val whereFilter: Op<Boolean> =
            if (searchCondition != null) groupFilter and searchCondition else groupFilter

        val count = baseJoin.selectAll().where { whereFilter }.count()

        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val dataQuery = baseJoin
            .select(Students.columns)
            .where { whereFilter }
            .orderBy(Students.id)
            .limit(PAGE_SIZE)
            .offset(offset)

        val members = Student.wrapRows(dataQuery)
            .with(Student::user, Student::groups)
            .toList()

        members to count
    }

    @Transactional
    override fun getManagers(groupId: Int, page: Int, query: String?): Pair<List<Teacher>, Long> = transaction {
        Group.assertExists(groupId)

        val searchCondition = query?.takeIf { it.isNotBlank() }?.let { userSearchCondition(it) }

        val baseJoin = (TeacherGroups innerJoin Teachers innerJoin Users)
        val groupFilter: Op<Boolean> = TeacherGroups.group eq groupId
        val whereFilter: Op<Boolean> =
            if (searchCondition != null) groupFilter and searchCondition else groupFilter

        val count = baseJoin.selectAll().where { whereFilter }.count()

        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val dataQuery = baseJoin
            .select(Teachers.columns)
            .where { whereFilter }
            .orderBy(Teachers.id)
            .limit(PAGE_SIZE)
            .offset(offset)

        val managers = Teacher.wrapRows(dataQuery)
            .with(Teacher::user, Teacher::groups)
            .toList()

        managers to count
    }

    override fun getMemberCounts() = StudentGroups.select(StudentGroups.group, StudentGroups.student.count())
        .groupBy(StudentGroups.group)
        .associate { it[StudentGroups.group].value to it[StudentGroups.student.count()].toInt() }

    override fun getMemberCount(groupId: Int): Int =
        StudentGroups.selectAll()
            .where { StudentGroups.group eq groupId }
            .count().toInt()

    @Transactional
    override fun deleteMembers(groupId: Int) {
        transaction {
            Group.assertExists(groupId)

            val studentIds = StudentGroups
                .select(StudentGroups.student)
                .where { StudentGroups.group eq groupId }
                .map { it[StudentGroups.student].value }

            if (studentIds.isEmpty()) return@transaction

            Users.deleteWhere { Users.id inList studentIds }
        }
    }

    @Transactional
    override fun migrateMembers(groupId: Int, targetGroupId: Int) {
        transaction {
            if (groupId == targetGroupId) throw ConflictException(ExceptionEntity.GROUP)

            val sourceType = Group.getType(groupId)
                ?: throw EntityNotFoundException(ExceptionEntity.GROUP)
            val targetType = Group.getType(targetGroupId)
                ?: throw EntityNotFoundException(ExceptionEntity.GROUP)

            if (sourceType != targetType) throw ConflictException(ExceptionEntity.GROUP)

            val studentIds = StudentGroups
                .select(StudentGroups.student)
                .where { StudentGroups.group eq groupId }
                .map { it[StudentGroups.student].value }

            if (studentIds.isEmpty()) return@transaction

            StudentGroups.batchInsert(studentIds, ignore = true) {
                this[StudentGroups.student] = it
                this[StudentGroups.group] = targetGroupId
            }

            StudentGroups.deleteWhere { StudentGroups.group eq groupId }
        }
    }
}
