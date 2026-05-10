package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.api.RATE_LIMIT_ENROLLMENTS
import th.ac.bodin2.electives.api.RATE_LIMIT_ENROLLMENTS_SUBJECT_MEMBERS
import th.ac.bodin2.electives.api.services.EnrollmentService
import th.ac.bodin2.electives.api.services.EnrollmentService.QueryResult
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listUsersResponse
import th.ac.bodin2.electives.proto.api.EnrollmentsServiceKt.listResponse
import th.ac.bodin2.electives.proto.api.EnrollmentsServiceKt.listSubjectMembersResponse
import th.ac.bodin2.electives.proto.api.EnrollmentsServiceKt.listSubjectsResponse

val enrollmentsController = controller {
    val enrollmentService: EnrollmentService by dependencies

    routing {
        context(enrollmentService) {
            rateLimit(RATE_LIMIT_ENROLLMENTS) {
                get<Enrollments> { handleGetEnrollments() }
                get<Enrollments.Id> { handleGetEnrollment(it.id) }
                get<Enrollments.Id.Subjects> { handleGetEnrollmentSubjects(it.parent.id) }
                get<Enrollments.Id.Subjects.SubjectId> { handleGetEnrollmentSubject(it.parent.parent.id, it.subjectId) }
            }

            authenticatedRoutes {
                rateLimit(RATE_LIMIT_ENROLLMENTS_SUBJECT_MEMBERS) {
                    get<Enrollments.Id.Subjects.SubjectId.Members> {
                        handleGetEnrollmentSubjectMembers(
                            enrollmentId = it.parent.parent.parent.id,
                            subjectId = it.parent.subjectId,
                            withStudents = it.withStudents == true,
                        )
                    }
                }

                get<Enrollments.Id.UnenrolledMembers> {
                    authenticated(ELEVATED_USER_ONLY) { _ ->
                        handleGetUnenrolledMembers(it.parent.id, it.group, it.page)
                    }
                }
            }
        }
    }
}

context(enrollmentService: EnrollmentService)
suspend fun RoutingContext.handleGetEnrollments() {
    call.respond(listResponse {
        transaction {
            enrollments += enrollmentService.getAll().map { it.toProto() }
        }
    })
}

context(enrollmentService: EnrollmentService)
suspend fun RoutingContext.handleGetEnrollment(enrollmentId: Int) {
    val response = transaction { enrollmentService.getById(enrollmentId)?.toProto() }
        ?: return enrollmentNotFoundError()

    call.respond(response)
}

context(enrollmentService: EnrollmentService)
suspend fun RoutingContext.handleGetEnrollmentSubjects(enrollmentId: Int) {
    val response = transaction {
        return@transaction when (val result = enrollmentService.getSubjects(enrollmentId)) {
            is QueryResult.EnrollmentNotFound -> null
            is QueryResult.Success ->
                listSubjectsResponse {
                    subjects += result.value.map {
                        it.toProto(
                            withDescription = false,
                            withTeachers = true,
                            enrollmentId = enrollmentId,
                            withEnrolledCounts = true,
                        )
                    }
                }

            else -> throw IllegalStateException("Unreachable case: $result")
        }
    } ?: return enrollmentNotFoundError()

    call.respond(response)
}

context(enrollmentService: EnrollmentService)
private suspend fun RoutingContext.handleGetEnrollmentSubject(enrollmentId: Int, subjectId: Int) {
    val response = transaction<RouteResponse> {
        when (val result = enrollmentService.getSubject(enrollmentId, subjectId)) {
            is QueryResult.EnrollmentNotFound -> Err(enrollmentNotFound)
            is QueryResult.SubjectNotFound -> Err(subjectNotFound)
            is QueryResult.SubjectNotPartOfEnrollment -> Err(subjectNotPartOfEnrollment(enrollmentId, subjectId))

            is QueryResult.Success -> Ok(
                result.value.toProto(
                    withDescription = true,
                    withTeachers = true,
                    enrollmentId = enrollmentId,
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

context(enrollmentService: EnrollmentService)
suspend fun RoutingContext.handleGetEnrollmentSubjectMembers(
    enrollmentId: Int,
    subjectId: Int,
    withStudents: Boolean,
) {
    val response = transaction<RouteResponse> {
        when (val result = enrollmentService.getSubjectMembers(enrollmentId, subjectId, withStudents)) {
            is QueryResult.EnrollmentNotFound -> Err(enrollmentNotFound)
            is QueryResult.SubjectNotFound -> Err(subjectNotFound)
            is QueryResult.SubjectNotPartOfEnrollment -> Err(subjectNotPartOfEnrollment(enrollmentId, subjectId))

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

context(enrollmentService: EnrollmentService)
suspend fun RoutingContext.handleGetUnenrolledMembers(
    enrollmentId: Int,
    groupId: Int,
    page: Int
) {
    val response = transaction<RouteResponse> {
        try {
            when (val result = enrollmentService.getUnenrolledMembers(enrollmentId, groupId, page)) {
                is QueryResult.EnrollmentNotFound -> Err(enrollmentNotFound)
                is QueryResult.Success -> {
                    val (students, count) = result.value
                    Ok(listUsersResponse {
                        users += students.map { it.toProto() }
                        total = count.toInt()
                    })
                }

                else -> throw IllegalStateException("Unreachable case: $result")
            }
        } catch (e: EntityNotFoundException) {
            Err(ErrorResponse(HttpStatusCode.BadRequest, "Group not found"))
        } catch (e: IllegalArgumentException) {
            Err(ErrorResponse(HttpStatusCode.BadRequest, e.message ?: "Invalid request"))
        }
    }

    when (response) {
        is Err -> return error(response.response)
        is Ok -> call.respond(response.response)
    }
}

private val enrollmentNotFound: ErrorResponse
    get() = ErrorResponse(
        status = HttpStatusCode.NotFound,
        message = "Enrollment not found",
    )

private val subjectNotFound: ErrorResponse
    get() = ErrorResponse(
        status = HttpStatusCode.NotFound,
        message = "Subject not found",
    )

private fun subjectNotPartOfEnrollment(enrollmentId: Int, subjectId: Int) = ErrorResponse(
    status = HttpStatusCode.BadRequest,
    message = "Subject $subjectId is not part of enrollment $enrollmentId",
)

private suspend inline fun RoutingContext.enrollmentNotFoundError() = error(enrollmentNotFound)

@Suppress("UNUSED")
@Resource("/enrollments")
private class Enrollments {
    @Resource("{id}")
    class Id(val parent: Enrollments = Enrollments(), val id: Int) {
        @Resource("subjects")
        class Subjects(val parent: Id) {
            @Resource("{subjectId}")
            class SubjectId(val parent: Subjects, val subjectId: Int) {
                @Resource("members")
                class Members(val parent: SubjectId, @SerialName("with_students") val withStudents: Boolean? = null)
            }
        }

        @Resource("unenrolled-members")
        class UnenrolledMembers(val parent: Id, val group: Int, val page: Int = 1)
    }
}
