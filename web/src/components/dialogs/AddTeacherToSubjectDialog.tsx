import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import AddUserDialog from './AddUserDialog'
import type { AdminSubjectPatch } from '../../api'

export default function AddTeacherToSubjectDialog(props: {
    open: boolean
    onClose: () => unknown
    subjectId: number
    electiveId: number
    currentTeacherIds: number[]
}) {
    const api = useAPI()
    const { string } = useI18n()
    const enrolledCounts = useEnrollmentCounts()

    return (
        <AddUserDialog
            open={props.open}
            onClose={props.onClose}
            headline={string.ADD_TEACHER_TO_SUBJECT()}
            actionLabel={string.ADD_TEACHER_TO_SUBJECT()}
            idLabel={string.TEACHER_ID()}
            validateUser={user => (!user.isTeacher() ? 'Not a teacher' : null)}
            onConfirm={async user => {
                // Check if already teaching
                if (props.currentTeacherIds.includes(user.id)) {
                    props.onClose()
                    return false
                }

                const newTeachers = [...props.currentTeacherIds, user.id]

                const patch: AdminSubjectPatch = {
                    teachers: newTeachers,
                    patchDescription: false,
                    patchCode: false,
                    patchLocation: false,
                    patchTeamId: false,
                    patchTeachers: true,
                    patchThumbnailUrl: false,
                    patchImageUrl: false,
                    electiveId: props.electiveId,
                }

                await api.client.subjects.admin.patch(props.subjectId, patch)
                enrolledCounts.bumpVersion(props.electiveId)
            }}
        />
    )
}
