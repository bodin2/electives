package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.RATE_LIMIT_ELECTIVES
import th.ac.bodin2.electives.api.RATE_LIMIT_ELECTIVES_SUBJECT_MEMBERS
import th.ac.bodin2.electives.api.services.ElectiveService
import th.ac.bodin2.electives.api.services.ElectiveService.QueryResult
import th.ac.bodin2.electives.api.utils.authenticatedRoutes
import th.ac.bodin2.electives.api.utils.badRequest
import th.ac.bodin2.electives.api.utils.notFound
import th.ac.bodin2.electives.api.utils.respond
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectMembersResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectsResponse

fun Application.registerElectivesRoutes() {
    val electiveService: ElectiveService by dependencies

    ElectivesController(electiveService).apply { register() }
}

class ElectivesController(private val electiveService: ElectiveService) {
    fun Application.register() {
        routing {
            rateLimit(RATE_LIMIT_ELECTIVES) {
                get<Electives> { handleGetElectives() }
                get<Electives.Id> { handleGetElective(it.id) }
                get<Electives.Id.Subjects> { handleGetElectiveSubjects(it.parent.id) }
                get<Electives.Id.Subjects.SubjectId> { handleGetElectiveSubject(it.parent.parent.id, it.subjectId) }
            }

            authenticatedRoutes {
                rateLimit(RATE_LIMIT_ELECTIVES_SUBJECT_MEMBERS) {
                    get<Electives.Id.Subjects.SubjectId.Members> {
                        handleGetElectiveSubjectMembers(
                            electiveId = it.parent.parent.parent.id,
                            subjectId = it.parent.subjectId,
                            withStudents = it.with_students == true,
                        )
                    }
                }
            }
        }
    }

    private suspend fun RoutingContext.handleGetElectives() {
        call.respond(listResponse {
            transaction {
                electives += electiveService.getAll().map { it.toProto() }
            }
        })
    }

    private suspend fun RoutingContext.handleGetElective(electiveId: Int) {
        val elective = transaction { electiveService.getById(electiveId) }
            ?: return electiveNotFoundError()

        call.respond(elective.toProto())
    }

    private suspend fun RoutingContext.handleGetElectiveSubjects(electiveId: Int) {
        val result = transaction { electiveService.getSubjects(electiveId) }
        when (result) {
            is QueryResult.ElectiveNotFound -> return electiveNotFoundError()
            is QueryResult.Success ->
                call.respond(listSubjectsResponse {
                    subjects += result.value.map { it.toProto(withDescription = false) }
                })

            else -> throw IllegalStateException("Unreachable case: $result")
        }
    }

    private suspend fun RoutingContext.handleGetElectiveSubject(electiveId: Int, subjectId: Int) {
        val result = transaction { electiveService.getSubject(electiveId, subjectId) }
        when (result) {
            is QueryResult.ElectiveNotFound -> return electiveNotFoundError()
            is QueryResult.SubjectNotFound -> return subjectNotFoundError()
            is QueryResult.SubjectNotPartOfElective -> return badRequest("Subject ${result.subjectId} is not part of elective ${result.electiveId}")
            is QueryResult.Success -> call.respond(listSubjectMembersResponse {
                call.respond(result.value.toProto(withDescription = true))
            })
        }
    }

    private suspend fun RoutingContext.handleGetElectiveSubjectMembers(
        electiveId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ) {
        val result = transaction { electiveService.getSubjectMembers(electiveId, subjectId, withStudents) }
        when (result) {
            is QueryResult.ElectiveNotFound -> return electiveNotFoundError()
            is QueryResult.SubjectNotFound -> return subjectNotFoundError()
            is QueryResult.SubjectNotPartOfElective -> return badRequest("Subject ${result.subjectId} is not part of elective ${result.electiveId}")
            is QueryResult.Success -> call.respond(listSubjectMembersResponse {
                val (teachers, students) = result.value

                this.teachers += teachers.map { it.toProto() }
                this.students += students.map { it.toProto() }
            })
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
                    class Members(val parent: SubjectId, val with_students: Boolean? = null)
                }
            }
        }
    }
}

private suspend inline fun RoutingContext.electiveNotFoundError() = notFound("Elective not found")
private suspend inline fun RoutingContext.subjectNotFoundError() = notFound("Subject not found")