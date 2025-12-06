package th.ac.bodin2.electives.db

import com.google.protobuf.ByteString
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.proto.api.SubjectTag
import th.ac.bodin2.electives.proto.api.UserType
import java.time.LocalDateTime
import java.time.ZoneOffset

fun Student.toProto(): th.ac.bodin2.electives.proto.api.User = transaction {
    val builder = th.ac.bodin2.electives.proto.api.User.newBuilder()
        .setId(user.id.value)
        .setFirstName(user.firstName)
        .setType(UserType.STUDENT)

    if (user.middleName != null) {
        builder.setMiddleName(user.middleName)
    }

    if (user.lastName != null) {
        builder.setLastName(user.lastName)
    }

    teams.forEach { builder.addTeams(it.toProto()) }

    builder.build()
}

fun Teacher.toProto(): th.ac.bodin2.electives.proto.api.User = transaction {
    val builder = th.ac.bodin2.electives.proto.api.User.newBuilder()
        .setId(user.id.value)
        .setFirstName(user.firstName)
        .setType(UserType.TEACHER)

    if (user.middleName != null) {
        builder.setMiddleName(user.middleName)
    }

    if (user.lastName != null) {
        builder.setLastName(user.lastName)
    }

    avatar?.let { builder.setAvatar(ByteString.copyFrom(it)) }

    builder.build()
}

/**
 * Convert Subject DAO to Proto representation.
 *
 * @param electiveId Optional elective ID to include enrolled count.
 * @param withDescription Whether to include the description field.
 */
fun Subject.toProto(electiveId: Int? = null, withDescription: Boolean = true): th.ac.bodin2.electives.proto.api.Subject {
    val subjectId = id.value

    return transaction {
        val builder = th.ac.bodin2.electives.proto.api.Subject.newBuilder()
            .setId(subjectId)
            .setName(name)
            .setCode(code)
            .setTag(SubjectTag.forNumber(tag))
            .setLocation(location)
            .setCapacity(capacity)

        if (withDescription) {
            builder.setDescription(description)
        }

        electiveId?.let { builder.setEnrolledCount(getEnrolledCount(it)) }

        teams.forEach { team -> builder.addTeamIds(team.id.value) }

        builder.build()
    }
}

fun Team.toProto(): th.ac.bodin2.electives.proto.api.Team {
    val teamId = id.value

    return transaction {
        val builder = th.ac.bodin2.electives.proto.api.Team.newBuilder()
            .setId(teamId)
            .setName(name)

        builder.build()
    }
}

fun Elective.toProto(): th.ac.bodin2.electives.proto.api.Elective {
    val electiveId = id.value

    return transaction {
        val builder = th.ac.bodin2.electives.proto.api.Elective.newBuilder()
            .setId(electiveId)
            .setName(name)

        team?.let { builder.setTeamId(it.id.value) }

        startDate?.let { builder.setStartDate(it.toUnixTimestamp()) }
        endDate?.let { builder.setEndDate(it.toUnixTimestamp()) }

        builder.build()
    }
}

internal fun LocalDateTime.toUnixTimestamp(): Long {
    return this.atZone(ZoneOffset.UTC).toEpochSecond()
}