import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import AddUserDialog from './AddUserDialog'

export default function AddStudentToSubjectDialog(props: {
    open: boolean
    onClose: () => unknown
    electiveId: number
    subjectId: number
}) {
    const api = useAPI()
    const { string } = useI18n()
    const enrolledCounts = useEnrollmentCounts()

    return (
        <AddUserDialog
            open={props.open}
            onClose={props.onClose}
            headline={string.ADD_STUDENT_TO_SUBJECT()}
            actionLabel={string.ADD_STUDENT_TO_SUBJECT()}
            idLabel={string.STUDENT_ID()}
            validateUser={user => (!user.isStudent() ? 'Not a student' : null)}
            onConfirm={async user => {
                await api.client.selections.set(user.id, props.electiveId, props.subjectId)
                enrolledCounts.bumpVersion(props.electiveId)
            }}
        />
    )
}
