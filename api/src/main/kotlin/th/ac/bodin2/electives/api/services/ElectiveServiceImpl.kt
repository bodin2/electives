package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.ElectiveService.QueryResult
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher

class ElectiveServiceImpl : ElectiveService {
    override fun getAll() = Elective.all().toList()

    override fun getById(electiveId: Int) = Elective.findById(electiveId)

    override fun getSubjects(electiveId: Int): QueryResult<out List<Subject>> {
        try {
            val elective = Elective.require(electiveId)
            return QueryResult.Success(Elective.getSubjects(elective))
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.ELECTIVE -> QueryResult.ElectiveNotFound
                else -> throw e
            }
        }
    }

    override fun getSubject(electiveId: Int, subjectId: Int): QueryResult<out Subject> {
        try {
            val elective = Elective.require(electiveId)
            val subject = Subject.require(subjectId)

            if (!Subject.isPartOfElective(subject, elective)) {
                return QueryResult.SubjectNotPartOfElective(subject.id, elective.id)
            }

            return QueryResult.Success(Subject.findById(subject.id)!!)
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.SUBJECT -> QueryResult.SubjectNotFound
                NotFoundEntity.ELECTIVE -> QueryResult.ElectiveNotFound
                else -> throw e
            }
        }
    }

    override fun getSubjectMembers(
        electiveId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ): QueryResult<out Pair<List<Teacher>, List<Student>>> {
        try {
            val elective = Elective.require(electiveId)
            val subject = Subject.require(subjectId)

            if (!Subject.isPartOfElective(subject, elective)) {
                return QueryResult.SubjectNotPartOfElective(subject.id, elective.id)
            }

            val teachers = Subject.getTeachers(subject)
            val students = if (withStudents) Subject.getStudents(subject, elective) else emptyList()

            return QueryResult.Success(teachers to students)
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.SUBJECT -> QueryResult.SubjectNotFound
                NotFoundEntity.ELECTIVE -> QueryResult.ElectiveNotFound
                else -> throw e
            }
        }
    }
}