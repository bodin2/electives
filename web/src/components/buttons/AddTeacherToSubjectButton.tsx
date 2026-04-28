import PlusIcon from '@iconify-icons/mdi/plus-circle'
import { Button, type ButtonVariant } from 'm3-solid'
import { createSignal, type JSX, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import AddTeacherToSubjectDialog from '../dialogs/AddTeacherToSubjectDialog'

export default function AddTeacherToSubjectButton(props: {
    class?: string
    style?: string | JSX.CSSProperties
    subjectId: number
    electiveId?: number
    currentTeacherIds: number[]
    variant?: ButtonVariant
    disabled?: boolean
}) {
    const { string } = useI18n()
    const [open, setOpen] = createSignal(false)

    return (
        <>
            <Button
                icon={PlusIcon}
                class={props.class}
                style={props.style}
                size="m"
                onClick={() => setOpen(true)}
                variant={props.variant}
                disabled={props.disabled}
            >
                {string.ADD_TEACHER_TO_SUBJECT()}
            </Button>
            <Show when={props.electiveId}>
                <Portal>
                    <AddTeacherToSubjectDialog
                        open={open()}
                        onClose={() => setOpen(false)}
                        subjectId={props.subjectId}
                        electiveId={nonNull(props.electiveId)}
                        currentTeacherIds={props.currentTeacherIds}
                    />
                </Portal>
            </Show>
        </>
    )
}
