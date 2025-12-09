package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.utils.notFound
import th.ac.bodin2.electives.api.utils.respond
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectMembersResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectsResponse

private suspend inline fun RoutingContext.electiveNotFoundError() = notFound("Elective not found")

fun Application.registerElectivesRoutes() {
    routing {
        get<Electives> { handleGetElectives() }
        get<Electives.Id> { handleGetElective(it.id) }
        get<Electives.Id.Subjects> { handleGetElectiveSubjects(it.parent.id) }
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

private suspend fun RoutingContext.handleGetElectives() {
    val electives = Elective.all()

    call.respond(listResponse {
        this.electives.addAll(electives.map { it.toProto() })
    })
}

private suspend fun RoutingContext.handleGetElective(electiveId: Int) {
    val elective = Elective.findById(electiveId) ?: return electiveNotFoundError()

    call.respond(elective.toProto())
}

private suspend fun RoutingContext.handleGetElectiveSubjects(electiveId: Int) {
    try {
        val subjects = Elective.getSubjects(electiveId)

        call.respond(listSubjectsResponse {
            this.subjects.addAll(subjects.map { it.toProto(withDescription = false) })
        })
    } catch (_: NotFoundException) {
        return electiveNotFoundError()
    }
}

private suspend fun RoutingContext.handleGetElectiveSubject(electiveId: Int, subjectId: Int) {
    val subject = Subject.findById(subjectId) ?: return notFound("Subject not found")
    call.respond(subject.toProto(electiveId))
}

private suspend fun RoutingContext.handleGetElectiveSubjectMembers(
    electiveId: Int,
    subjectId: Int,
    withStudents: Boolean,
) {
    try {
        val teachers = Subject.getTeachers(subjectId)
        val students = if (withStudents) Subject.getStudents(subjectId, electiveId) else emptyList()

        call.respond(listSubjectMembersResponse {
            this.teachers.addAll(teachers.map { it.toProto() })
            this.students.addAll(students.map { it.toProto() })
        })
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
        }
    }
}