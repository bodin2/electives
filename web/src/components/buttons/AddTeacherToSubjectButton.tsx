import PlusIcon from '@iconify-icons/mdi/plus-circle'
import { Button, type ButtonVariant } from 'm3-solid/src'
import { createSignal, type JSX, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import AddTeacherToSubjectDialog from '../dialogs/AddTeacherToSubjectDialog'
import { useSubjectInfoContext } from '../subjects/SubjectInfo'

export default function AddTeacherToSubjectButton(props: {
    class?: string
    style?: string | JSX.CSSProperties
    subjectId: number
    enrollmentId?: number
    variant?: ButtonVariant
    disabled?: boolean
    onInvalidate?: () => Promise<unknown> | unknown
    currentTeacherIds?: number[]
}) {
    const { string } = useI18n()
    const ctx = useSubjectInfoContext()
    const [open, setOpen] = createSignal(false)

    const currentTeacherIds = () => {
        if (props.currentTeacherIds) return props.currentTeacherIds
        return ctx.teachers?.map(t => t.id) ?? []
    }

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
            <Show when={props.enrollmentId}>
                <Portal>
                    <AddTeacherToSubjectDialog
                        open={open()}
                        onClose={() => setOpen(false)}
                        subjectId={props.subjectId}
                        enrollmentId={nonNull(props.enrollmentId)}
                        currentTeacherIds={currentTeacherIds()}
                        onInvalidate={props.onInvalidate}
                    />
                </Portal>
            </Show>
        </>
    )
}
