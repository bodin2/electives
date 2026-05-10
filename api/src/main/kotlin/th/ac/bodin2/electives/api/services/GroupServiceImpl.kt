package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
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
import th.ac.bodin2.electives.db.models.Groups
import th.ac.bodin2.electives.db.models.StudentGroups
import th.ac.bodin2.electives.db.models.Students
import th.ac.bodin2.electives.db.models.Users

class GroupServiceImpl : GroupService {
    companion object {
        private const val PAGE_SIZE = 50
    }

    @Transactional
    override fun create(
        id: Int,
        name: String,
    ) = transaction {
        val stmt = Groups.insertIgnore {
            it[this.id] = id
            it[this.name] = name
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.GROUP)
        Group.wrapRow(stmt.resultedValues!!.first())
    }

    @Transactional
    override fun delete(id: Int) {
        transaction {
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

            if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
        }
        Group.wrapRow(rows.first())
    }

    override fun getAll() = Group.all().toList()

    override fun getById(groupId: Int) = Group.findById(groupId)

    @Transactional
    override fun getMembers(groupId: Int, page: Int, query: String?): Pair<List<Student>, Long> = transaction {
        Group.findById(groupId) ?: throw EntityNotFoundException(ExceptionEntity.GROUP)

        val searchCondition = query?.takeIf { it.isNotBlank() }?.let { userSearchCondition(it) }
        val groupCondition = StudentGroups.group eq groupId

        val countQuery = if (searchCondition != null) {
            (StudentGroups innerJoin Students innerJoin Users)
                .selectAll().where { groupCondition and searchCondition }
        } else {
            StudentGroups.selectAll().where { groupCondition }
        }
        val count = countQuery.count()

        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val dataQuery = (StudentGroups innerJoin Students innerJoin Users)
            .selectAll().where {
                if (searchCondition != null) groupCondition and searchCondition else groupCondition
            }
            .limit(PAGE_SIZE)
            .offset(offset)

        val members = Student.wrapRows(dataQuery)
            .with(Student::user, Student::groups)
            .toList()

        members to count
    }

    override fun getMemberCounts() = StudentGroups.select(StudentGroups.group, StudentGroups.student.count())
        .groupBy(StudentGroups.group)
        .associate { it[StudentGroups.group].value to it[StudentGroups.student.count()].toInt() }

    override fun getMemberCount(groupId: Int): Int =
        StudentGroups.selectAll()
            .where { StudentGroups.group eq groupId }
            .count().toInt()
}
