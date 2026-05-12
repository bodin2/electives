import { useAPI } from '~/providers/APIProvider'
import { useEnrollmentCounts } from '~/providers/EnrollmentCountsProvider'
import { useI18n } from '~/providers/I18nProvider'
import AddUserDialog from './base/AddUserDialog'

export default function AddStudentToSubjectDialog(props: {
    open: boolean
    onClose: () => unknown
    enrollmentId: number
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
            type="student"
            onConfirm={async user => {
                await api.client.selections.set(user.id, props.enrollmentId, props.subjectId)
                enrolledCounts.setCount(props.enrollmentId, props.subjectId, current => current + 1)
            }}
        />
    )
}
