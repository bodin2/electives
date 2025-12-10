package th.ac.bodin2.electives.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.db.models.*

class User(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, User>(Users)

    var firstName by Users.firstName
    var middleName by Users.middleName
    var lastName by Users.lastName

    var passwordHash by Users.passwordHash
    var sessionHash by Users.sessionHash
}

data class Student(val id: Int, val user: User) {
    enum class CanEnrollStatus {
        CAN_ENROLL,

        /**
         * The subject is not part of the elective.
         */
        SUBJECT_NOT_IN_ELECTIVE,
        ALREADY_ENROLLED,

        /**
         * The student is not part of the elective's team.
         */
        NOT_IN_ELECTIVE_TEAM,

        /**
         * The student is not part of the subject's team.
         */
        NOT_IN_SUBJECT_TEAM,

        /**
         * The subject is already full.
         */
        SUBJECT_FULL,
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Student::class.java)

        /// PUBLIC API

        fun exists(id: Int): Boolean = Students.selectAll().where { Students.id eq id }.count() > 0L

        fun findById(id: Int): Student? {
            if (!exists(id)) return null

            val user = User.findById(id) ?: return null
            return Student(id, user)
        }

        fun hasTeam(studentId: Int, teamId: Int): Boolean {
            if (!exists(studentId)) throw NotFoundException(NotFoundEntity.STUDENT)

            return StudentTeams
                .selectAll().where { (StudentTeams.student eq studentId) and (StudentTeams.team eq teamId) }
                .count() > 0L
        }

        fun getAllElectiveSelections(studentId: Int): List<Pair<Elective, Subject>> {
            if (!exists(studentId)) throw NotFoundException(NotFoundEntity.STUDENT)

            return StudentElectives
                .selectAll().where { StudentElectives.student eq studentId }
                .map { Elective.findById(it[StudentElectives.elective])!! to Subject.findById(it[StudentElectives.subject])!! }
        }

        fun getElectiveSelectionId(studentId: Int, electiveId: Int): Int? {
            if (!exists(studentId)) throw NotFoundException(NotFoundEntity.STUDENT)

            return StudentElectives
                .selectAll()
                .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
                .singleOrNull()
                ?.get(StudentElectives.subject)
                ?.value
        }

        fun setElectiveSelection(studentId: Int, electiveId: Int, subjectId: Int) {
            if (!exists(studentId)) throw NotFoundException(NotFoundEntity.STUDENT)

            val updated = StudentElectives.update(
                where = { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) },
                body = { it[StudentElectives.subject] = subjectId }
            )

            if (updated == 0) {
                StudentElectives.insert {
                    it[StudentElectives.student] = studentId
                    it[StudentElectives.elective] = electiveId
                    it[StudentElectives.subject] = subjectId
                }
            }

            logger.info("Student elective selection, user: $studentId, elective: $electiveId, subject: $subjectId")
        }

        /**
         * Removes a selection from the specified elective.
         *
         * @throws NotFoundException if the selection does not exist.
         */
        fun removeElectiveSelection(studentId: Int, electiveId: Int) {
            if (!exists(studentId)) throw NotFoundException(NotFoundEntity.STUDENT)

            val count = StudentElectives.deleteWhere {
                (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId)
            }

            if (count == 0) throw NotFoundException(NotFoundEntity.ELECTIVE_SELECTION)

            logger.info("Student elective deselection, user: $studentId, elective: $electiveId")
        }

        /**
         * Checks if the student can enroll in the specified subject of the elective by:
         *
         * 1. Checking if the subject is part of the elective.
         * 2. Checking if the student is already enrolled in another subject of the same elective.
         * 3. Checking if the student is part of the elective's team (if any).
         * 4. Checking if the student is part of the subject's team (if any).
         * 5. Checking if the subject is full.
         *
         * @throws NotFoundException if the student does not exist.
         */
        fun canEnrollInSubject(studentId: Int, electiveId: Int, subjectId: Int): CanEnrollStatus {
            if (!exists(studentId)) throw NotFoundException(NotFoundEntity.STUDENT)

            if (!Subject.isPartOfElective(subjectId, electiveId))
                return CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE

            // Check if the student is already enrolled in another subject of the same elective
            val alreadyEnrolled = StudentElectives
                .selectAll()
                .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
                .count() > 0L

            if (alreadyEnrolled) return CanEnrollStatus.ALREADY_ENROLLED

            // Check if the student is part of the elective's team (if any)
            val electiveTeamId = Elective.getTeamId(electiveId)
            if (electiveTeamId != null && !hasTeam(studentId, electiveTeamId))
                return CanEnrollStatus.NOT_IN_ELECTIVE_TEAM

            // Check if the student is part of the subject's team (if any)
            val subjectTeamId = Subject.getTeamId(subjectId)
            if (subjectTeamId != null && !hasTeam(studentId, subjectTeamId))
                return CanEnrollStatus.NOT_IN_SUBJECT_TEAM

            if (Subject.isFull(subjectId, electiveId))
                return CanEnrollStatus.SUBJECT_FULL

            return CanEnrollStatus.CAN_ENROLL
        }

        fun new(id: Int, user: User): Student {
            Students.insert {
                it[Students.id] = id
            }

            return Student(id, user)
        }
    }

    val teams
        get(): List<Team> {
            if (!exists(this@Student.id)) throw NotFoundException(NotFoundEntity.STUDENT)

            return (StudentTeams innerJoin Teams)
                .selectAll().where { StudentTeams.student eq this@Student.id }
                .map { Team.wrapRow(it) }
        }

    val selections
        get() = getAllElectiveSelections(id)
}

class Teacher(val id: Int, val user: User, val avatar: ByteArray?) {
    companion object {
        fun findById(id: Int): Teacher? {
            val teacher = Teachers
                .selectAll().where { Teachers.id eq id }
                .singleOrNull()
                ?: return null

            val user = User.findById(id) ?: return null
            return Teacher(id, user, teacher[Teachers.avatar]?.bytes)
        }

        fun getSubjects(teacherId: Int): List<Subject> {
            return (TeacherSubjects innerJoin Subjects)
                .selectAll().where { TeacherSubjects.teacher eq teacherId }
                .map { Subject.wrapRow(it) }
        }

        fun teachesSubject(teacherId: Int, subjectId: Int): Boolean {
            return TeacherSubjects
                .selectAll().where { (TeacherSubjects.teacher eq teacherId) and (TeacherSubjects.subject eq subjectId) }
                .count() > 0L
        }

        fun new(id: Int, user: User, avatar: ByteArray? = null): Teacher {
            Teachers.insert {
                it[Teachers.id] = id
                it[Teachers.avatar] = avatar?.let { bytes -> ExposedBlob(bytes) }
            }

            return Teacher(id, user, avatar)
        }
    }

    val subjects
        get() = getSubjects(id)
}

class Team(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Team>(Teams)

    var name by Teams.name
}

open class ElectiveCompanion : EntityClass<Int, Elective>(Electives) {
    private fun getSubjectsInTxn(electiveId: Int): List<Subject> =
        (ElectiveSubjects innerJoin Subjects)
            .selectAll().where { ElectiveSubjects.elective eq electiveId }
            .map { Subject.wrapRow(it) }

    private fun getSubjectIdsInTxn(electiveId: Int): List<Int> =
        (ElectiveSubjects innerJoin Subjects)
            .select(Subjects.id)
            .where { ElectiveSubjects.elective eq electiveId }
            .map { it[Subjects.id].value }

    private fun getEnrolledCountForSubjectInTxn(subjectId: Int): Int =
        StudentElectives
            .selectAll().where { StudentElectives.subject eq subjectId }
            .count()
            .toInt()


    /// PUBLIC API

    fun exists(id: Int): Boolean =
        Electives.selectAll().where { Electives.id eq id }.count() > 0L

    fun getAllIds(): List<Int> {
        return Electives.select(Electives.id).map { it[Electives.id].value }
    }

    /**
     * Gets the team ID for the specified elective.
     *
     * @throws NotFoundException if the elective does not exist.
     */
    fun getTeamId(electiveId: Int): Int? {
        if (!exists(electiveId))
            throw NotFoundException(NotFoundEntity.ELECTIVE)

        return Electives
            .select(Electives.team)
            .where { Electives.id eq electiveId }
            .singleOrNull()
            ?.get(Electives.team)?.value
    }

    fun getSubjectIds(electiveId: Int): List<Int> {
        if (!exists(electiveId)) throw NotFoundException(NotFoundEntity.ELECTIVE)

        return getSubjectIdsInTxn(electiveId)
    }

    /**
     * Gets the subjects for the specified elective.
     *
     * @throws NotFoundException if the elective does not exist.
     */
    fun getSubjects(electiveId: Int): List<Subject> {
        if (!exists(electiveId)) throw NotFoundException(NotFoundEntity.ELECTIVE)

        return getSubjectsInTxn(electiveId)
    }

    /**
     * Gets all subject IDs and their enrolled counts for the specified elective.
     *
     * @throws NotFoundException if the elective does not exist.
     *
     * @return Map<SubjectId, EnrolledCount>
     */
    fun getSubjectsEnrolledCounts(electiveId: Int): Map<Int, Int> {
        if (!exists(electiveId)) throw NotFoundException(NotFoundEntity.ELECTIVE)

        val subjectIds = getSubjectIdsInTxn(electiveId)
        return subjectIds.associateWith { getEnrolledCountForSubjectInTxn(it) }
    }
}

class Elective(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : ElectiveCompanion()

    var name by Electives.name
    val team by Team optionalReferencedOn Electives.team

    val subjects by Subject via ElectiveSubjects

    var startDate by Electives.startDate
    var endDate by Electives.endDate
}

open class SubjectCompanion : EntityClass<Int, Subject>(Subjects) {
    private fun getCapacityInTxn(subjectId: Int): Int =
        Subjects
            .select(Subjects.capacity)
            .where { Subjects.id eq subjectId }
            .single()[Subjects.capacity]

    private fun getEnrolledCountInTxn(subjectId: Int, electiveId: Int): Int =
        StudentElectives
            .selectAll().where { (StudentElectives.subject eq subjectId) and (StudentElectives.elective eq electiveId) }
            .count()
            .toInt()

    /// PUBLIC API

    fun exists(id: Int): Boolean =
        Subjects.selectAll().where { Subjects.id eq id }.count() > 0L

    /**
     * Gets the team ID for the specified subject.
     *
     * @throws NotFoundException if the subject does not exist.
     */
    fun getTeamId(subjectId: Int): Int? {
        if (!exists(subjectId)) throw NotFoundException(NotFoundEntity.SUBJECT)

        return Subjects
            .select(Subjects.team)
            .where { Subjects.id eq subjectId }
            .single()[Subjects.team]?.value
    }

    /**
     * Gets teachers for the specified subject.
     *
     * @throws NotFoundException if the subject does not exist or is not part of the elective.
     */
    fun getTeachers(subjectId: Int): List<Teacher> {
        if (!exists(subjectId)) throw NotFoundException(NotFoundEntity.SUBJECT)

        return (TeacherSubjects innerJoin Teachers)
            .selectAll().where { (TeacherSubjects.subject eq subjectId) }
            .map { Teacher.findById(it[Teachers.id].value)!! }
    }

    /**
     * Gets students for the specified subject of the specified elective.
     *
     * @throws NotFoundException if the subject does not exist.
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getStudents(subjectId: Int, electiveId: Int): List<Student> {
        if (!exists(subjectId)) throw NotFoundException(NotFoundEntity.SUBJECT)
        if (!isPartOfElective(subjectId, electiveId)) throw Subject.NotPartOfElectiveException(subjectId, electiveId)

        return (StudentElectives innerJoin Students)
            .selectAll().where {
                (StudentElectives.subject eq subjectId) and
                        (StudentElectives.elective eq electiveId)
            }
            .map { Student.findById(it[Students.id].value)!! }
    }

    /**
     * Checks if the subject is part of the specified elective.
     *
     * @throws NotFoundException if the subject does not exist.
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun isPartOfElective(subjectId: Int, electiveId: Int): Boolean {
        if (!exists(subjectId)) throw NotFoundException(NotFoundEntity.SUBJECT)

        return (ElectiveSubjects innerJoin Subjects)
            .selectAll().where { (ElectiveSubjects.elective eq electiveId) and (ElectiveSubjects.subject eq subjectId) }
            .count() > 0L
    }

    /**
     * Checks if the subject is full for the specified elective.
     *
     * @throws NotFoundException if the subject does not exist or is not part of the elective.
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun isFull(subjectId: Int, electiveId: Int): Boolean {
        if (!exists(subjectId)) throw NotFoundException(NotFoundEntity.SUBJECT)
        if (!isPartOfElective(subjectId, electiveId)) throw Subject.NotPartOfElectiveException(subjectId, electiveId)

        val capacity = getCapacityInTxn(subjectId)
        val enrolled = getEnrolledCountInTxn(subjectId, electiveId)
        return enrolled >= capacity
    }

    /**
     * Gets the enrolled count for the specified subject of the specified elective.
     *
     * @throws NotFoundException if the subject does not exist.
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getEnrolledCount(subjectId: Int, electiveId: Int): Int {
        if (!exists(subjectId)) throw NotFoundException(NotFoundEntity.SUBJECT)
        if (!isPartOfElective(subjectId, electiveId)) throw Subject.NotPartOfElectiveException(subjectId, electiveId)

        return getEnrolledCountInTxn(subjectId, electiveId)
    }
}

class Subject(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : SubjectCompanion()

    class NotPartOfElectiveException(subjectId: Int, electiveId: Int) : Exception("Subject $subjectId is not part of elective $electiveId")

    var name by Subjects.name

    fun getStudents(electiveId: Int) = getStudents(id.value, electiveId)
    fun getEnrolledCount(electiveId: Int) = getEnrolledCount(id.value, electiveId)

    val teachers: List<Teacher>
        get() = getTeachers(id.value)

    var description by Subjects.description
    var code by Subjects.code
    var tag by Subjects.tag
    var team by Subjects.team

    var location by Subjects.location
    var capacity by Subjects.capacity
}