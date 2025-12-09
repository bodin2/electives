package th.ac.bodin2.electives.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
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

        private fun existsInTxn(id: Int): Boolean = Students.selectAll().where { Students.id eq id }.count() > 0L

        /// PUBLIC API

        fun exists(id: Int): Boolean = transaction { existsInTxn(id) }

        fun findById(id: Int): Student? = transaction {
            if (!existsInTxn(id)) return@transaction null

            val user = User.findById(id) ?: return@transaction null
            Student(id, user)
        }

        fun hasTeam(studentId: Int, teamId: Int): Boolean = transaction {
            if (!existsInTxn(studentId)) throw NotFoundException("Student does not exist")

            StudentTeams
                .selectAll().where { (StudentTeams.student eq studentId) and (StudentTeams.team eq teamId) }
                .count() > 0L
        }

        fun getAllElectiveSelections(studentId: Int): List<Pair<Elective, Subject>> = transaction {
            if (!existsInTxn(studentId)) throw NotFoundException("Student does not exist")

            StudentElectives
                .selectAll().where { StudentElectives.student eq studentId }
                .map { Elective.wrapRow(it) to Subject.wrapRow(it) }
        }

        fun getElectiveSelectionId(studentId: Int, electiveId: Int): Int? = transaction {
            if (!existsInTxn(studentId)) throw NotFoundException("Student does not exist")

            StudentElectives
                .selectAll()
                .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
                .singleOrNull()
                ?.get(StudentElectives.subject)
                ?.value
        }

        fun setElectiveSelection(studentId: Int, electiveId: Int, subjectId: Int) {
            transaction {
                if (!existsInTxn(studentId)) throw NotFoundException("Student does not exist")

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
            }

            logger.info("Student elective selection, user: $studentId, elective: $electiveId, subject: $subjectId")
        }

        /**
         * Removes a selection from the specified elective.
         *
         * @throws NotFoundException if the selection does not exist.
         */
        fun removeElectiveSelection(studentId: Int, electiveId: Int) {
            val count = transaction {
                if (!existsInTxn(studentId)) throw NotFoundException("Student does not exist")

                StudentElectives.deleteWhere {
                    (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId)
                }
            }

            if (count == 0) throw NotFoundException("Student elective selection does not exist")

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
         */
        fun canEnrollInSubject(studentId: Int, electiveId: Int, subjectId: Int): CanEnrollStatus {
            if (!exists(studentId)) throw NotFoundException("Student does not exist")

            if (!Subject.isPartOfElective(subjectId, electiveId))
                return CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE

            // Check if the student is already enrolled in another subject of the same elective
            val alreadyEnrolled = transaction {
                StudentElectives
                    .selectAll()
                    .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
                    .count() > 0L
            }

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
            transaction {
                Students.insert {
                    it[Students.id] = id
                }
            }

            return Student(id, user)
        }
    }

    val teams
        get() = transaction {
            if (!existsInTxn(this@Student.id)) throw NotFoundException("Student does not exist")

            (StudentTeams innerJoin Teams)
                .selectAll().where { StudentTeams.student eq this@Student.id }
                .map { Team.wrapRow(it) }
        }

    val selections
        get() = getAllElectiveSelections(id)
}

class Teacher(val id: Int, val user: User, val avatar: ByteArray?) {
    companion object {
        fun findById(id: Int): Teacher? = transaction {
            val teacher = Teachers
                .selectAll().where { Teachers.id eq id }
                .singleOrNull()
                ?: return@transaction null

            val user = User.findById(id) ?: return@transaction null
            Teacher(id, user, teacher[Teachers.avatar]?.bytes)
        }

        fun getSubjects(teacherId: Int): List<Subject> = transaction {
            (TeacherSubjects innerJoin Subjects)
                .selectAll().where { TeacherSubjects.teacher eq teacherId }
                .map { Subject.wrapRow(it) }
        }

        fun teachesSubject(teacherId: Int, subjectId: Int): Boolean = transaction {
            TeacherSubjects
                .selectAll().where { (TeacherSubjects.teacher eq teacherId) and (TeacherSubjects.subject eq subjectId) }
                .count() > 0L
        }

        fun new(id: Int, user: User, avatar: ByteArray? = null): Teacher {
            transaction {
                Teachers.insert {
                    it[Teachers.id] = id
                    it[Teachers.avatar] = avatar?.let { bytes -> ExposedBlob(bytes) }
                }
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
    private fun existsInTxn(id: Int): Boolean =
        Electives.selectAll().where { Electives.id eq id }.count() > 0L

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

    fun exists(id: Int): Boolean = transaction { existsInTxn(id) }

    fun getAllIds(): List<Int> = transaction {
        Electives.select(Electives.id).map { it[Electives.id].value }
    }

    /**
     * Gets the team ID for the specified elective.
     *
     * @throws NotFoundException if the elective does not exist.
     */
    fun getTeamId(electiveId: Int): Int? = transaction {
        if (!existsInTxn(electiveId))
            throw NotFoundException("Elective does not exist")

        Electives
            .select(Electives.team)
            .where { Electives.id eq electiveId }
            .singleOrNull()
            ?.get(Electives.team)?.value
    }

    fun getSubjectIds(electiveId: Int): List<Int> = transaction {
        if (!existsInTxn(electiveId)) throw NotFoundException("Elective does not exist")

        getSubjectIdsInTxn(electiveId)
    }

    /**
     * Gets the subjects for the specified elective.
     *
     * @throws NotFoundException if the elective does not exist.
     */
    fun getSubjects(electiveId: Int): List<Subject> = transaction {
        if (!existsInTxn(electiveId)) throw NotFoundException("Elective does not exist")

        getSubjectsInTxn(electiveId)
    }

    /**
     * Gets all subject IDs and their enrolled counts for the specified elective.
     *
     * @throws NotFoundException if the elective does not exist.
     *
     * @return Map<SubjectId, EnrolledCount>
     */
    fun getSubjectsEnrolledCounts(electiveId: Int): Map<Int, Int> = transaction {
        if (!existsInTxn(electiveId)) throw NotFoundException("Elective does not exist")

        val subjectIds = getSubjectIdsInTxn(electiveId)
        subjectIds.associateWith { getEnrolledCountForSubjectInTxn(it) }
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
    private fun existsInTxn(id: Int): Boolean =
        Subjects.selectAll().where { Subjects.id eq id }.count() > 0L

    private fun isPartOfElectiveInTxn(subjectId: Int, electiveId: Int): Boolean =
        (ElectiveSubjects innerJoin Subjects)
            .selectAll().where { (ElectiveSubjects.elective eq electiveId) and (ElectiveSubjects.subject eq subjectId) }
            .count() > 0L

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

    fun exists(id: Int): Boolean = transaction { existsInTxn(id) }

    /**
     * Gets the team ID for the specified subject.
     *
     * @throws NotFoundException if the subject does not exist.
     */
    fun getTeamId(subjectId: Int): Int? = transaction {
        if (!existsInTxn(subjectId)) throw NotFoundException("Subject does not exist")

        Subjects
            .select(Subjects.team)
            .where { Subjects.id eq subjectId }
            .single()[Subjects.team]?.value
    }

    /**
     * Gets teachers for the specified subject.
     *
     * @throws NotFoundException if the subject does not exist or is not part of the elective.
     */
    fun getTeachers(subjectId: Int): List<Teacher> = transaction {
        if (!existsInTxn(subjectId)) throw NotFoundException("Subject does not exist")

        (TeacherSubjects innerJoin Teachers)
            .selectAll().where { (TeacherSubjects.subject eq subjectId) }
            .map { Teacher.findById(it[Teachers.id].value)!! }
    }

    /**
     * Gets students for the specified subject of the specified elective.
     *
     * @throws NotFoundException if the subject does not exist or is not part of the elective.
     */
    fun getStudents(subjectId: Int, electiveId: Int): List<Student> = transaction {
        if (!existsInTxn(subjectId)) throw NotFoundException("Subject does not exist")
        if (!isPartOfElectiveInTxn(
                subjectId,
                electiveId
            )
        ) throw NotFoundException("Subject is not part of the elective")

        (StudentElectives innerJoin Students)
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
     */
    fun isPartOfElective(subjectId: Int, electiveId: Int): Boolean = transaction {
        if (!existsInTxn(subjectId)) throw NotFoundException("Subject does not exist")

        isPartOfElectiveInTxn(subjectId, electiveId)
    }

    /**
     * Checks if the subject is full for the specified elective.
     *
     * @throws NotFoundException if the subject does not exist or is not part of the elective.
     */
    fun isFull(subjectId: Int, electiveId: Int): Boolean = transaction {
        if (!existsInTxn(subjectId)) throw NotFoundException("Subject does not exist")
        if (!isPartOfElectiveInTxn(
                subjectId,
                electiveId
            )
        ) throw NotFoundException("Subject is not part of the elective")

        val capacity = getCapacityInTxn(subjectId)
        val enrolled = getEnrolledCountInTxn(subjectId, electiveId)
        enrolled >= capacity
    }

    /**
     * Gets the enrolled count for the specified subject of the specified elective.
     *
     * @throws NotFoundException if the subject does not exist or is not part of the elective.
     */
    fun getEnrolledCount(subjectId: Int, electiveId: Int): Int = transaction {
        if (!existsInTxn(subjectId)) throw NotFoundException("Subject does not exist")
        if (!isPartOfElectiveInTxn(
                subjectId,
                electiveId
            )
        ) throw NotFoundException("Subject is not part of the elective")

        getEnrolledCountInTxn(subjectId, electiveId)
    }
}

class Subject(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : SubjectCompanion()

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