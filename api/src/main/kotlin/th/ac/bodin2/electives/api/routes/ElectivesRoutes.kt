package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
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
import th.ac.bodin2.electives.api.utils.*
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
                context(electiveService) {
                    get<Electives> { handleGetElectives() }
                    get<Electives.Id> { handleGetElective(it.id) }
                }
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

    private suspend fun RoutingContext.handleGetElectiveSubjects(electiveId: Int) {
        val response = transaction {
            return@transaction when (val result = electiveService.getSubjects(electiveId)) {
                is QueryResult.ElectiveNotFound -> null
                is QueryResult.Success ->
                    listSubjectsResponse {
                        subjects += result.value.map { it.toProto(withDescription = false, withTeachers = true, electiveId = electiveId) }
                    }

                else -> throw IllegalStateException("Unreachable case: $result")
            }
        } ?: return electiveNotFoundError()

        call.respond(response)
    }

    private suspend fun RoutingContext.handleGetElectiveSubject(electiveId: Int, subjectId: Int) {
        val response = transaction<RouteResponse> {
            when (val result = electiveService.getSubject(electiveId, subjectId)) {
                is QueryResult.ElectiveNotFound -> Err(electiveNotFound)
                is QueryResult.SubjectNotFound -> Err(subjectNotFound)
                is QueryResult.SubjectNotPartOfElective -> Err(subjectNotPartOfElective(electiveId, subjectId))

                is QueryResult.Success -> Ok(result.value.toProto(withDescription = true, withTeachers = true))
            }
        }

        when (response) {
            is Err -> return error(response.response)
            is Ok -> call.respond(response.response)
        }
    }

    private suspend fun RoutingContext.handleGetElectiveSubjectMembers(
        electiveId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ) {
        val response = transaction<RouteResponse> {
            when (val result = electiveService.getSubjectMembers(electiveId, subjectId, withStudents)) {
                is QueryResult.ElectiveNotFound -> Err(electiveNotFound)
                is QueryResult.SubjectNotFound -> Err(subjectNotFound)
                is QueryResult.SubjectNotPartOfElective -> Err(subjectNotPartOfElective(electiveId, subjectId))

                is QueryResult.Success -> Ok(listSubjectMembersResponse {
                    val (teachers, students) = result.value

                    this.teachers += teachers.map { it.toProto() }
                    this.students += students.map { it.toProto() }
                })
            }
        }

        when (response) {
            is Err -> return error(response.response)
            is Ok -> call.respond(response.response)
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

context(electiveService: ElectiveService)
suspend fun RoutingContext.handleGetElectives() {
    call.respond(listResponse {
        transaction {
            electives += electiveService.getAll().map { it.toProto() }
        }
    })
}

context(electiveService: ElectiveService)
suspend fun RoutingContext.handleGetElective(electiveId: Int) {
    val response = transaction { electiveService.getById(electiveId)?.toProto() }
        ?: return electiveNotFoundError()

    call.respond(response)
}

private val electiveNotFound: ErrorResponse
    get() = ErrorResponse(
        status = HttpStatusCode.NotFound,
        message = "Elective not found",
    )

private val subjectNotFound: ErrorResponse
    get() = ErrorResponse(
        status = HttpStatusCode.NotFound,
        message = "Subject not found",
    )

private fun subjectNotPartOfElective(subjectId: Int, electiveId: Int) = ErrorResponse(
    status = HttpStatusCode.BadRequest,
    message = "Subject $subjectId is not part of elective $electiveId",
)

private suspend inline fun RoutingContext.electiveNotFoundError() = error(electiveNotFound)