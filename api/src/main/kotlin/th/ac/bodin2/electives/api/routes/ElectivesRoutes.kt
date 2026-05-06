package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.api.RATE_LIMIT_ELECTIVES
import th.ac.bodin2.electives.api.RATE_LIMIT_ELECTIVES_SUBJECT_MEMBERS
import th.ac.bodin2.electives.api.services.ElectiveService
import th.ac.bodin2.electives.api.services.ElectiveService.QueryResult
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.ElectivesService

val electivesController = controller {
    val electiveService: ElectiveService by dependencies

    routing {
        context(electiveService) {
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
                            withStudents = it.withStudents == true,
                        )
                    }
                }

                get<Electives.Id.UnenrolledMembers> {
                    authenticated(ELEVATED_USER_ONLY) { _ ->
                        handleGetUnenrolledMembers(it.parent.id, it.team, it.page)
                    }
                }
            }
        }
    }
}

context(electiveService: ElectiveService)
suspend fun RoutingContext.handleGetElectives() {
    call.respond(
        ElectivesService.ListResponse(
            electives = transaction {
                electiveService.getAll().map { it.toProto() }
            }
        ))
}

context(electiveService: ElectiveService)
suspend fun RoutingContext.handleGetElective(electiveId: UInt) {
    val response = transaction { electiveService.getById(electiveId)?.toProto() }
        ?: return electiveNotFoundError()

    call.respond(response)
}

context(electiveService: ElectiveService)
suspend fun RoutingContext.handleGetElectiveSubjects(electiveId: UInt) {
    val response = transaction {
        return@transaction when (val result = electiveService.getSubjects(electiveId)) {
            is QueryResult.ElectiveNotFound -> null
            is QueryResult.Success ->
                ElectivesService.ListSubjectsResponse(
                    subjects = result.value.map {
                        it.toProto(
                            withDescription = false,
                            withTeachers = true,
                            electiveId = electiveId,
                            withEnrolledCounts = true,
                        )
                    }
                )

            else -> throw IllegalStateException("Unreachable case: $result")
        }
    } ?: return electiveNotFoundError()

    call.respond(response)
}

context(electiveService: ElectiveService)
private suspend fun RoutingContext.handleGetElectiveSubject(electiveId: UInt, subjectId: UInt) {
    val response = transaction<RouteResponse> {
        when (val result = electiveService.getSubject(electiveId, subjectId)) {
            is QueryResult.ElectiveNotFound -> Err(electiveNotFound)
            is QueryResult.SubjectNotFound -> Err(subjectNotFound)
            is QueryResult.SubjectNotPartOfElective -> Err(subjectNotPartOfElective(electiveId, subjectId))

            is QueryResult.Success -> Ok(
                result.value.toProto(
                    withDescription = true,
                    withTeachers = true,
                    electiveId = electiveId,
                    withEnrolledCounts = true,
                )
            )
        }
    }

    when (response) {
        is Err -> return error(response.response)
        is Ok -> call.respond(response.response)
    }
}

context(electiveService: ElectiveService)
suspend fun RoutingContext.handleGetElectiveSubjectMembers(
    electiveId: UInt,
    subjectId: UInt,
    withStudents: Boolean,
) {
    val response = transaction<RouteResponse> {
        when (val result = electiveService.getSubjectMembers(electiveId, subjectId, withStudents)) {
            is QueryResult.ElectiveNotFound -> Err(electiveNotFound)
            is QueryResult.SubjectNotFound -> Err(subjectNotFound)
            is QueryResult.SubjectNotPartOfElective -> Err(subjectNotPartOfElective(electiveId, subjectId))

            is QueryResult.Success -> Ok(
                ElectivesService.ListSubjectMembersResponse(
                    teachers = result.value.first.map { it.toProto() },
                    students = result.value.second.map { it.toProto() },
                )
            )
        }
    }

    when (response) {
        is Err -> return error(response.response)
        is Ok -> call.respond(response.response)
    }
}

context(electiveService: ElectiveService)
suspend fun RoutingContext.handleGetUnenrolledMembers(
    electiveId: UInt,
    teamId: UInt,
    page: Int
) {
    val response = transaction<RouteResponse> {
        try {
            when (val result = electiveService.getUnenrolledMembers(electiveId, teamId, page)) {
                is QueryResult.ElectiveNotFound -> Err(electiveNotFound)
                is QueryResult.Success -> {
                    val (students, count) = result.value
                    Ok(
                        AdminService.ListUsersResponse(
                            users = students.map { it.toProto() },
                            total = count.toInt(),
                        )
                    )
                }

                else -> throw IllegalStateException("Unreachable case: $result")
            }
        } catch (e: EntityNotFoundException) {
            Err(ErrorResponse(HttpStatusCode.BadRequest, "Team not found"))
        } catch (e: IllegalArgumentException) {
            Err(ErrorResponse(HttpStatusCode.BadRequest, e.message ?: "Invalid request"))
        }
    }

    when (response) {
        is Err -> return error(response.response)
        is Ok -> call.respond(response.response)
    }
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

private fun subjectNotPartOfElective(subjectId: UInt, electiveId: UInt) = ErrorResponse(
    status = HttpStatusCode.BadRequest,
    message = "Subject $subjectId is not part of elective $electiveId",
)

private suspend inline fun RoutingContext.electiveNotFoundError() = error(electiveNotFound)

@Suppress("UNUSED")
@Resource("/electives")
private class Electives {
    @Resource("{id}")
    class Id(
        val parent: Electives = Electives(),
        @Serializable(with = UIntStringSerializer::class)
        val id: UInt
    ) {
        @Resource("subjects")
        class Subjects(val parent: Id) {
            @Resource("{subjectId}")
            class SubjectId(
                val parent: Subjects,
                @Serializable(with = UIntStringSerializer::class)
                val subjectId: UInt
            ) {
                @Resource("members")
                class Members(val parent: SubjectId, @SerialName("with_students") val withStudents: Boolean? = null)
            }
        }

        @Resource("unenrolled-members")
        class UnenrolledMembers(
            val parent: Id,
            @Serializable(with = UIntStringSerializer::class)
            val team: UInt, val page: Int = 1
        )
    }
}