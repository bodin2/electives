package th.ac.bodin2.electives.db

import th.ac.bodin2.electives.proto.api.*
import java.time.LocalDateTime
import java.time.ZoneOffset

fun Student.toProto(): th.ac.bodin2.electives.proto.api.User {
    return user {
        id = user.id.value
        firstName = user.firstName
        type = UserType.STUDENT

        user.prefix?.let { prefix = it }
        user.middleName?.let { middleName = it }
        user.lastName?.let { lastName = it }

        user.avatarUrl?.let { avatarUrl = it }

        groups.addAll(this@toProto.groups.map { it.toProto() })
    }
}

fun Teacher.toProto(): th.ac.bodin2.electives.proto.api.User {
    return user {
        id = user.id.value
        firstName = user.firstName
        type = UserType.TEACHER

        user.prefix?.let { prefix = it }
        user.middleName?.let { middleName = it }
        user.lastName?.let { lastName = it }

        user.avatarUrl?.let { avatarUrl = it }
    }
}

fun Admin.toProto(): th.ac.bodin2.electives.proto.api.User {
    return user {
        id = user.id.value
        firstName = user.firstName
        type = UserType.ADMIN

        user.prefix?.let { prefix = it }
        user.middleName?.let { middleName = it }
        user.lastName?.let { lastName = it }

        user.avatarUrl?.let { avatarUrl = it }
    }
}

/**
 * Convert Subject DAO to Proto representation.
 *
 * **A transaction must be active when calling this function with `enrollmentId`, or `withDescription = true`**
 *
 * @param enrollmentId Optional enrollment ID to include enrolled count and teachers.
 * @param withTeachers Whether to include the teachers field (requires `enrollmentId`).
 * @param withDescription Whether to include the description field.
 * @param withEnrolledCounts Whether to include the enrolled count field.
 */
fun Subject.toProto(
    enrollmentId: Int? = null,
    withTeachers: Boolean = false,
    withDescription: Boolean = false,
    withEnrolledCounts: Boolean = false
): th.ac.bodin2.electives.proto.api.Subject {
    val subject = this

    return subject {
        id = subject.id.value
        name = subject.name
        tag = SubjectTag.forNumber(subject.tag)
        capacity = subject.capacity

        subject.location?.let { location = it }
        subject.code?.let { code = it }

        subject.thumbnailUrl?.let { thumbnailUrl = it }
        subject.imageUrl?.let { imageUrl = it }

        if (withDescription) {
            subject.description?.let { description = it }
        }

        if (withTeachers && enrollmentId != null) {
            subject.getTeachers(enrollmentId).forEach { teachers += it.toProto() }
        }

        this@toProto.groupId?.let { groupId = it.value }

        if (withEnrolledCounts && enrollmentId != null) {
            Enrollment.assertExists(enrollmentId)
            enrolledCount = getEnrolledCount(enrollmentId)
        }
    }
}

fun Group.toProto(): th.ac.bodin2.electives.proto.api.Group {
    val group = this

    return group {
        id = group.id.value
        name = group.name
    }
}

fun Enrollment.toProto(): th.ac.bodin2.electives.proto.api.Enrollment {
    val enrollment = this

    return enrollment {
        id = enrollment.id.value
        name = enrollment.name

        enrollment.groupId?.let { groupId = it.value }
        enrollment.startDate?.toUnixTimestamp()?.let { startDate = it }
        enrollment.endDate?.toUnixTimestamp()?.let { endDate = it }
    }
}

internal fun LocalDateTime.toUnixTimestamp(): Long {
    return this.atZone(ZoneOffset.UTC).toEpochSecond()
}
