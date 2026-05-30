package th.ac.bodin2.electives.db

import th.ac.bodin2.electives.proto.api.GroupType
import th.ac.bodin2.electives.proto.api.SubjectTag
import th.ac.bodin2.electives.proto.api.UserType
import java.time.LocalDateTime
import java.time.ZoneOffset

fun Student.toProto(): th.ac.bodin2.electives.proto.api.User {
    return th.ac.bodin2.electives.proto.api.User(
        id = user.id.value,
        first_name = user.firstName,
        type = UserType.STUDENT,
        prefix = user.prefix,
        middle_name = user.middleName,
        last_name = user.lastName,
        avatar_url = user.avatarUrl,
        groups = this.groups.map { it.toProto() },
    )
}

fun Teacher.toProto(): th.ac.bodin2.electives.proto.api.User {
    return th.ac.bodin2.electives.proto.api.User(
        id = user.id.value,
        first_name = user.firstName,
        type = UserType.TEACHER,
        prefix = user.prefix,
        middle_name = user.middleName,
        last_name = user.lastName,
        avatar_url = user.avatarUrl,
        groups = this.groups.map { it.toProto() },
    )
}

fun Admin.toProto(): th.ac.bodin2.electives.proto.api.User {
    return th.ac.bodin2.electives.proto.api.User(
        id = user.id.value,
        first_name = user.firstName,
        type = UserType.ADMIN,
        prefix = user.prefix,
        middle_name = user.middleName,
        last_name = user.lastName,
        avatar_url = user.avatarUrl,
    )
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

    val teachers = if (withTeachers && enrollmentId != null) {
        subject.getTeachers(enrollmentId).map { it.toProto() }
    } else emptyList()

    val enrolledCount = if (withEnrolledCounts && enrollmentId != null) {
        getEnrolledCount(enrollmentId)
    } else null

    return th.ac.bodin2.electives.proto.api.Subject(
        id = subject.id.value,
        name = subject.name,
        tag = SubjectTag.fromValue(subject.tag)!!,
        capacity = subject.capacity,
        location = subject.location ?: "",
        code = subject.code ?: "",
        thumbnail_url = subject.thumbnailUrl,
        image_url = subject.imageUrl,
        description = if (withDescription) subject.description else null,
        teachers = teachers,
        group_id = subject.groupId?.value,
        enrolled_count = enrolledCount,
    )
}

/**
 * Batched [Subject] -> Proto conversion for an entire enrollment listing.
 *
 * Builds all subjects in **a constant number of queries** regardless of list size:
 * teachers are fetched once for the whole enrollment ([Enrollment.getTeachersBySubject]) and
 * enrolled counts once ([Enrollment.getSubjectsEnrolledCounts]), instead of issuing per-subject
 * lookups (the previous N+1 pattern).
 *
 * **A transaction must be active when calling this function.**
 *
 * @param enrollmentId The enrollment the subjects belong to.
 * @param withTeachers Whether to include the teachers field.
 * @param withDescription Whether to include the description field (triggers a per-subject lazy load).
 * @param withEnrolledCounts Whether to include the enrolled count field.
 */
fun List<Subject>.toProto(
    enrollmentId: Int,
    withTeachers: Boolean = false,
    withDescription: Boolean = false,
    withEnrolledCounts: Boolean = false,
): List<th.ac.bodin2.electives.proto.api.Subject> {
    if (isEmpty()) return emptyList()

    val teachersBySubject =
        if (withTeachers) Enrollment.getTeachersBySubject(enrollmentId) else emptyMap()
    val enrolledCounts =
        if (withEnrolledCounts) Enrollment.getSubjectsEnrolledCounts(enrollmentId) else emptyMap()

    return map { subject ->
        val subjectId = subject.id.value

        th.ac.bodin2.electives.proto.api.Subject(
            id = subjectId,
            name = subject.name,
            tag = SubjectTag.fromValue(subject.tag)!!,
            capacity = subject.capacity,
            location = subject.location ?: "",
            code = subject.code ?: "",
            thumbnail_url = subject.thumbnailUrl,
            image_url = subject.imageUrl,
            description = if (withDescription) subject.description else null,
            teachers = if (withTeachers) teachersBySubject[subjectId].orEmpty().map { it.toProto() } else emptyList(),
            group_id = subject.groupId?.value,
            enrolled_count = if (withEnrolledCounts) (enrolledCounts[subjectId] ?: 0) else null,
        )
    }
}

fun Group.toProto(): th.ac.bodin2.electives.proto.api.Group {
    val group = this

    return th.ac.bodin2.electives.proto.api.Group(
        id = group.id.value,
        name = group.name,
        type = GroupType.fromValue(group.type) ?: GroupType.CUSTOM,
        parent_id = group.parentId?.value,
    )
}

fun Enrollment.toProto(): th.ac.bodin2.electives.proto.api.Enrollment {
    val enrollment = this

    return th.ac.bodin2.electives.proto.api.Enrollment(
        id = enrollment.id.value,
        name = enrollment.name,
        group_id = enrollment.groupId?.value,
        start_date = enrollment.startDate?.toUnixTimestamp(),
        end_date = enrollment.endDate?.toUnixTimestamp(),
    )
}

internal fun LocalDateTime.toUnixTimestamp(): Long {
    return this.atZone(ZoneOffset.UTC).toEpochSecond()
}
