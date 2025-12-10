package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.get
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.utils.badRequest
import th.ac.bodin2.electives.api.utils.notFound
import th.ac.bodin2.electives.api.utils.respond
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectMembersResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectsResponse

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

@Suppress("UNUSED")
@Resource("/electives")
private class Electives {
    @Resource("{id}")
    class Id(val parent: Electives = Electives(), val id: Int) {
        @Resource("subjects")
        class Subjects(val parent: Id) {
            @Resource("{subjectId}")
            class SubjectId(val parent: Subjects, val subjectId: Int) {
                @Resource("members")
                class Members(val parent: SubjectId, val with_students: Boolean? = null)
            }
        }
    }
}

private suspend fun RoutingContext.handleGetElectives() {
    call.respond(transaction {
        listResponse {
            electives.addAll(Elective.all().map { it.toProto() })
        }
    })
}

private suspend fun RoutingContext.handleGetElective(electiveId: Int) {
    val electiveProto = transaction {
        val elective = Elective.findById(electiveId) ?: return@transaction null
        elective.toProto()
    } ?: return electiveNotFoundError()

    call.respond(electiveProto)
}

private suspend fun RoutingContext.handleGetElectiveSubjects(electiveId: Int) {
    try {
        val response = transaction {
            val subjects = Elective.getSubjects(electiveId)

            listSubjectsResponse {
                this.subjects.addAll(subjects.map { it.toProto(withDescription = false) })
            }
        }

        call.respond(response)
    } catch (_: NotFoundException) {
        return electiveNotFoundError()
    }
}

private suspend fun RoutingContext.handleGetElectiveSubject(electiveId: Int, subjectId: Int) {
    val subjectProto = transaction {
        val subject = Subject.findById(subjectId) ?: return@transaction null
        subject.toProto(electiveId)
    } ?: return subjectNotFoundError()

    call.respond(subjectProto)
}

private suspend fun RoutingContext.handleGetElectiveSubjectMembers(
    electiveId: Int,
    subjectId: Int,
    withStudents: Boolean,
) {
    val result = transaction {
        if (!Elective.exists(electiveId))
            return@transaction GetElectiveSubjectMembersResult.ElectiveNotFound

        try {
            val teachers = Subject.getTeachers(subjectId)
            val students = if (withStudents) Subject.getStudents(subjectId, electiveId) else emptyList()

            GetElectiveSubjectMembersResult.Success(listSubjectMembersResponse {
                this.teachers.addAll(teachers.map { it.toProto() })
                this.students.addAll(students.map { it.toProto() })
            })

        } catch (e: NotFoundException) {
            return@transaction when (e.entity) {
                NotFoundEntity.SUBJECT -> GetElectiveSubjectMembersResult.SubjectNotFound
                else -> throw e
            }
        } catch (_: Subject.NotPartOfElectiveException) {
            return@transaction GetElectiveSubjectMembersResult.SubjectNotPartOfElective(subjectId, electiveId)
        }
    }

    when (result) {
        is GetElectiveSubjectMembersResult.ElectiveNotFound -> return electiveNotFoundError()
        is GetElectiveSubjectMembersResult.SubjectNotFound -> return subjectNotFoundError()
        is GetElectiveSubjectMembersResult.SubjectNotPartOfElective -> return badRequest("Subject ${result.subjectId} is not part of elective ${result.electiveId}")
        is GetElectiveSubjectMembersResult.Success -> call.respond(result.response)
    }
}

private sealed class GetElectiveSubjectMembersResult {
    data class Success(val response: th.ac.bodin2.electives.proto.api.ElectivesService.ListSubjectMembersResponse) :
        GetElectiveSubjectMembersResult()

    data object ElectiveNotFound : GetElectiveSubjectMembersResult()
    data object SubjectNotFound : GetElectiveSubjectMembersResult()
    data class SubjectNotPartOfElective(val subjectId: Int, val electiveId: Int) : GetElectiveSubjectMembersResult()
}

private suspend inline fun RoutingContext.electiveNotFoundError() = notFound("Elective not found")
private suspend inline fun RoutingContext.subjectNotFoundError() = notFound("Subject not found")