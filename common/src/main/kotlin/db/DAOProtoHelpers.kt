package th.ac.bodin2.electives.db

import com.google.protobuf.kotlin.toByteString
import th.ac.bodin2.electives.proto.api.*
import java.time.LocalDateTime
import java.time.ZoneOffset

fun Student.toProto(): th.ac.bodin2.electives.proto.api.User {
    val student = this

    return user {
        id = user.id.value
        firstName = user.firstName
        type = UserType.STUDENT

        user.middleName?.let { middleName = it }
        user.lastName?.let { lastName = it }

        teams.addAll(student.teams.map { it.toProto() })
    }
}

fun Teacher.toProto(): th.ac.bodin2.electives.proto.api.User {
    val teacher = this

    return user {
        id = user.id.value
        firstName = user.firstName
        type = UserType.TEACHER

        user.middleName?.let { middleName = it }
        user.lastName?.let { lastName = it }

        teacher.avatar?.let { avatar = it.toByteString() }
    }
}

/**
 * Convert Subject DAO to Proto representation.
 *
 * **A transaction must be active when calling this function with `electiveId`.**
 *
 * @param electiveId Optional elective ID to include enrolled count.
 * @param withDescription Whether to include the description field.
 */
fun Subject.toProto(
    electiveId: Int? = null,
    withDescription: Boolean = true
): th.ac.bodin2.electives.proto.api.Subject {
    val subject = this

    return subject {
        id = subject.id.value
        name = subject.name
        tag = SubjectTag.forNumber(subject.tag)
        capacity = subject.capacity

        subject.location?.let { location = it }
        subject.code?.let { code = it }

        if (withDescription) {
            subject.description?.let { description = it }
        }

        this@toProto.teamId?.let { teamId = it.value }
        electiveId?.let { enrolledCount = getEnrolledCount(Elective.require(it)) }
    }
}

fun Team.toProto(): th.ac.bodin2.electives.proto.api.Team {
    val team = this

    return team {
        id = team.id.value
        name = team.name
    }
}

fun Elective.toProto(): th.ac.bodin2.electives.proto.api.Elective {
    val elective = this

    return elective {
        id = elective.id.value
        name = elective.name

        elective.team?.let { teamId = it.id.value }
        elective.startDate?.toUnixTimestamp()?.let { startDate = it }
        elective.endDate?.toUnixTimestamp()?.let { endDate = it }
    }
}

internal fun LocalDateTime.toUnixTimestamp(): Long {
    return this.atZone(ZoneOffset.UTC).toEpochSecond()
}