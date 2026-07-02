package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.utils.dbQuery
import th.ac.bodin2.electives.db.Group
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.*
import th.ac.bodin2.electives.proto.api.GroupType

class GroupService {
    companion object {
        private const val PAGE_SIZE = 50
    }

    /**
     * Creates a new group with the given information.
     *
     * @throws ConflictException if a group with the same ID already exists.
     */
    @Transactional
    suspend fun create(
        id: Int,
        name: String,
        type: GroupType = GroupType.CUSTOM,
        parentId: Int? = null,
    ) = dbQuery {
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

    /**
     * Deletes a group by its ID.
     *
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    suspend fun delete(id: Int) {
        dbQuery {
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

    /**
     * Updates a group's information.
     *
     * @throws EntityNotFoundException if the group does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    suspend fun update(id: Int, update: GroupUpdate) = dbQuery {
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

    data class GroupUpdate(
        val name: String? = null,
        val parentId: Int? = null,
        val setParentId: Boolean = false,
    )

    fun getAll() = Group.all().toList()

    fun getById(groupId: Int) = Group.findById(groupId)

    /**
     * Gets a paginated list of group members, optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @return Pair of member list and total count.
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    suspend fun getMembers(groupId: Int, page: Int = 1, query: String? = null): Pair<List<Student>, Long> = dbQuery {
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

    /**
     * Gets a paginated list of group managers (teachers), optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @return Pair of teacher list and total count.
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    suspend fun getManagers(groupId: Int, page: Int = 1, query: String? = null): Pair<List<Teacher>, Long> = dbQuery {
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

    /**
     * Gets every [Group] the teacher is a manager of. Ordered by group ID.
     *
     * @throws EntityNotFoundException if the teacher does not exist.
     */
    @Transactional
    suspend fun getTeacherGroups(teacherId: Int): List<Group> = dbQuery {
        Teacher.assertExists(teacherId)

        val query = (TeacherGroups innerJoin Groups)
            .select(Groups.columns)
            .where { TeacherGroups.teacher eq teacherId }
            .orderBy(Groups.id)

        Group.wrapRows(query).toList()
    }

    fun getMemberCounts() = StudentGroups.select(StudentGroups.group, StudentGroups.student.count())
        .groupBy(StudentGroups.group)
        .associate { it[StudentGroups.group].value to it[StudentGroups.student.count()].toInt() }

    fun getMemberCount(groupId: Int): Int =
        StudentGroups.selectAll()
            .where { StudentGroups.group eq groupId }
            .count().toInt()

    /**
     * Deletes all members of the specified group by removing the underlying
     * [th.ac.bodin2.electives.db.User] rows. Cascading deletes also remove the
     * associated [th.ac.bodin2.electives.db.Student] rows and their group
     * memberships. The group itself is left in place.
     *
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    suspend fun deleteMembers(groupId: Int) {
        dbQuery {
            Group.assertExists(groupId)

            val studentIds = StudentGroups
                .select(StudentGroups.student)
                .where { StudentGroups.group eq groupId }
                .map { it[StudentGroups.student].value }

            if (studentIds.isEmpty()) return@dbQuery

            Users.deleteWhere { Users.id inList studentIds }
        }
    }

    /**
     * Moves all members of [groupId] into [targetGroupId]. The target group must
     * exist, be different from the source, and have the same [GroupType] as the
     * source. Memberships already present in the target are preserved.
     *
     * @throws EntityNotFoundException if either group does not exist.
     * @throws ConflictException if the groups are the same or have different types.
     */
    @Transactional
    suspend fun migrateMembers(groupId: Int, targetGroupId: Int) {
        dbQuery {
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

            if (studentIds.isEmpty()) return@dbQuery

            StudentGroups.batchInsert(studentIds, ignore = true) {
                this[StudentGroups.student] = it
                this[StudentGroups.group] = targetGroupId
            }

            StudentGroups.deleteWhere { StudentGroups.group eq groupId }
        }
    }
}
