import { useAPI } from '~/providers/APIProvider'
import { useEnrollmentCounts } from '~/providers/EnrollmentCountsProvider'
import { useI18n } from '~/providers/I18nProvider'
import AddUserDialog from './base/AddUserDialog'
import type { AdminSubjectPatch } from '~/api'

export default function AddTeacherToSubjectDialog(props: {
    open: boolean
    onClose: () => unknown
    subjectId: number
    enrollmentId: number
    currentTeacherIds: number[]
    onInvalidate?: () => Promise<unknown> | unknown
}) {
    const api = useAPI()
    const { string } = useI18n()
    const enrolledCounts = useEnrollmentCounts()

    return (
        <AddUserDialog
            open={props.open}
            onClose={props.onClose}
            headline={string.ADD_TEACHER_TO_SUBJECT()}
            type="teacher"
            disabledIds={props.currentTeacherIds}
            onConfirm={async user => {
                const newTeachers = [...props.currentTeacherIds, user.id]

                const patch: AdminSubjectPatch = {
                    teachers: newTeachers,
                    patchDescription: false,
                    patchCode: false,
                    patchLocation: false,
                    patchGroupId: false,
                    patchTeachers: true,
                    patchThumbnailUrl: false,
                    patchImageUrl: false,
                    enrollmentId: props.enrollmentId,
                }

                await api.client.subjects.admin.patch(props.subjectId, patch)
                enrolledCounts.bumpVersion(props.enrollmentId)
                await props.onInvalidate?.()
            }}
        />
    )
}
