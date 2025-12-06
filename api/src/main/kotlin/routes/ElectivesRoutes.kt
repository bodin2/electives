package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.utils.badRequest
import th.ac.bodin2.electives.api.utils.respondMessage
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.ElectivesService

private suspend inline fun RoutingContext.electiveNotFoundError() = badRequest("Elective not found")

fun Application.registerElectivesRoutes() {
    routing {
        get<Electives.Id> { handleGetElective(it.id) }
        get<Electives.Id.Subjects> { handleGetElectiveSubjects(it.parent.id) }
        get<Electives.Id.Subjects.EnrolledCounts> { handleGetElectiveSubjectEnrolledCounts(it.parent.parent.id) }
        get<Electives.Id.Subjects.SubjectId> { handleGetElectiveSubject(it.parent.parent.id, it.subjectId) }
        get<Electives.Id.Subjects.SubjectId.Members> {
            handleGetElectiveSubjectMembers(
                electiveId = it.parent.parent.parent.id,
                subjectId = it.parent.subjectId,
                withStudents = it.with_students == true,
            )
        }
    }
}

private suspend fun RoutingContext.handleGetElective(electiveId: Int) {
    val elective = Elective.findById(electiveId)
        ?: return electiveNotFoundError()

    call.respondMessage(elective.toProto())
}

private suspend fun RoutingContext.handleGetElectiveSubjects(electiveId: Int) {
    try {
        val subjects = Elective.getSubjects(electiveId)

        call.respondMessage(
            ElectivesService.ListSubjectsResponse.newBuilder().apply {
                subjects.map { addSubjects(it.toProto(withDescription = false)) }
            }.build()
        )
    } catch (_: NotFoundException) {
        return electiveNotFoundError()
    }
}

private suspend fun RoutingContext.handleGetElectiveSubjectEnrolledCounts(electiveId: Int) {
    try {
        val counts = Elective.getSubjectsEnrolledCounts(electiveId)

        call.respondMessage(
            ElectivesService.ListSubjectsCountsResponse.newBuilder().apply {
                counts.forEach { (subjectId, count) -> putSubjectEnrolledCounts(subjectId, count) }
            }.build()
        )
    } catch (_: NotFoundException) {
        return electiveNotFoundError()
    }
}

private suspend fun RoutingContext.handleGetElectiveSubject(electiveId: Int, subjectId: Int) {
    val subject = Subject.findById(subjectId) ?: return badRequest("Subject not found")
    call.respondMessage(subject.toProto(electiveId))
}

private suspend fun RoutingContext.handleGetElectiveSubjectMembers(
    electiveId: Int,
    subjectId: Int,
    withStudents: Boolean,
) {
    try {
        val teachers = Subject.getTeachers(subjectId, electiveId)
        val students = if (withStudents) Subject.getStudents(subjectId, electiveId) else emptyList()

        call.respondMessage(
            ElectivesService.ListSubjectMembersResponse.newBuilder().apply {
                teachers.map { addTeachers(it.toProto()) }
                students.map { addStudents(it.toProto()) }
            }.build()
        )
    } catch (_: NotFoundException) {
        return electiveNotFoundError()
    }
}

@Suppress("UNUSED")
@Resource("/electives")
class Electives {
    @Resource("{id}")
    class Id(val parent: Electives = Electives(), val id: Int) {
        @Resource("subjects")
        class Subjects(val parent: Id) {
            @Resource("{subjectId}")
            class SubjectId(val parent: Subjects, val subjectId: Int) {
                @Resource("members")
                class Members(val parent: SubjectId, val with_students: Boolean? = false)
            }

            @Resource("enrolled_counts")
            class EnrolledCounts(val parent: Subjects)
        }
    }
}