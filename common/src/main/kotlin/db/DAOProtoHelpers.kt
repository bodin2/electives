package th.ac.bodin2.electives.db

import th.ac.bodin2.electives.proto.api.*
import java.time.LocalDateTime
import java.time.ZoneOffset

fun Student.toProto(): th.ac.bodin2.electives.proto.api.User {
    return th.ac.bodin2.electives.proto.api.User(
        id = user.id.value.toInt(),
        first_name = user.firstName,
        type = UserType.STUDENT,
        middle_name = user.middleName,
        last_name = user.lastName,
        avatar_url = user.avatarUrl,
        teams = this.teams.map { it.toProto() },
    )
}

fun Teacher.toProto(): th.ac.bodin2.electives.proto.api.User {
    return th.ac.bodin2.electives.proto.api.User(
        id = user.id.value.toInt(),
        first_name = user.firstName,
        type = UserType.TEACHER,
        middle_name = user.middleName,
        last_name = user.lastName,
        avatar_url = user.avatarUrl,
    )
}

fun Admin.toProto(): th.ac.bodin2.electives.proto.api.User {
    return th.ac.bodin2.electives.proto.api.User(
        id = user.id.value.toInt(),
        first_name = user.firstName,
        type = UserType.ADMIN,
        middle_name = user.middleName,
        last_name = user.lastName,
        avatar_url = user.avatarUrl,
    )
}

/**
 * Convert Subject DAO to Proto representation.
 *
 * **A transaction must be active when calling this function with `electiveId`, or `withDescription = true`**
 *
 * @param electiveId Optional elective ID to include enrolled count and teachers.
 * @param withTeachers Whether to include the teachers field (requires `electiveId`).
 * @param withDescription Whether to include the description field.
 * @param withEnrolledCounts Whether to include the enrolled count field.
 */
fun Subject.toProto(
    electiveId: UInt? = null,
    withTeachers: Boolean = false,
    withDescription: Boolean = false,
    withEnrolledCounts: Boolean = false
): th.ac.bodin2.electives.proto.api.Subject {
    val subject = this

    return th.ac.bodin2.electives.proto.api.Subject(
        id = subject.id.value.toInt(),
        name = subject.name,
        tag = SubjectTag.fromValue(subject.tag) ?: SubjectTag.THAI,
        capacity = subject.capacity,
        location = subject.location ?: "",
        code = subject.code ?: "",
        thumbnail_url = subject.thumbnailUrl,
        image_url = subject.imageUrl,
        description = if (withDescription) subject.description else null,
        teachers = if (withTeachers && electiveId != null) subject.getTeachers(electiveId).map { it.toProto() } else emptyList(),
        team_id = this.teamId?.value?.toInt(),
        enrolled_count = if (withEnrolledCounts && electiveId != null) {
            Elective.assertExists(electiveId)
            getEnrolledCount(electiveId)
        } else null,
    )
}

fun Team.toProto(): th.ac.bodin2.electives.proto.api.Team {
    return th.ac.bodin2.electives.proto.api.Team(
        id = this.id.value.toInt(),
        name = this.name,
    )
}

fun Elective.toProto(): th.ac.bodin2.electives.proto.api.Elective {
    return th.ac.bodin2.electives.proto.api.Elective(
        id = this.id.value.toInt(),
        name = this.name,
        team_id = this.teamId?.value?.toInt(),
        start_date = this.startDate?.toUnixTimestamp(),
        end_date = this.endDate?.toUnixTimestamp(),
    )
}

internal fun LocalDateTime.toUnixTimestamp(): Long {
    return this.atZone(ZoneOffset.UTC).toEpochSecond()
}
