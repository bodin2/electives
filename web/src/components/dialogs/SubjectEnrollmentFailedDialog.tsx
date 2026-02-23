import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'

export default function SubjectEnrollmentFailedDialog(props: { reason: string | null; onClose: () => void }) {
    const { string } = useI18n()

    return (
        <Dialog
            headline={string.ENROLLMENT_FAILED_TITLE()}
            onClose={props.onClose}
            open={Boolean(props.reason)}
            actions={<Button onClick={props.onClose}>{string.CLOSE()}</Button>}
            closedBy="any"
        >
            {/** biome-ignore lint/style/noNonNullAssertion: Will never be rendered */}
            <p class="text-ws-pre-line">{string.ENROLLMENT_FAILED_DESCRIPTION({ reason: props.reason! })}</p>
        </Dialog>
    )
}
