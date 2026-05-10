import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { Button, type ButtonVariant } from 'm3-solid'
import { createSignal, type JSX, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import AddStudentToSubjectDialog from '../dialogs/AddStudentToSubjectDialog'

export default function AddStudentToSubjectButton(props: {
    variant?: ButtonVariant
    class?: string
    style?: string | JSX.CSSProperties
    enrollmentId?: number
    subjectId: number
    disabled?: boolean
}) {
    const { string } = useI18n()
    const [open, setOpen] = createSignal(false)

    return (
        <>
            <Button
                disabled={props.disabled}
                icon={AddCircleIcon}
                class={props.class}
                style={props.style}
                size="m"
                onClick={() => setOpen(true)}
                variant={props.variant}
            >
                {string.ADD_STUDENT_TO_SUBJECT()}
            </Button>
            <Show when={props.enrollmentId}>
                <Portal>
                    <AddStudentToSubjectDialog
                        open={/* @once */ open() && !props.disabled}
                        onClose={() => setOpen(false)}
                        enrollmentId={nonNull(props.enrollmentId)}
                        subjectId={props.subjectId}
                    />
                </Portal>
            </Show>
        </>
    )
}
