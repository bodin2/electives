import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { Button } from 'm3-solid'
import { createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import AddStudentToSubjectDialog from '../dialogs/AddStudentToSubjectDialog'

export default function AddStudentToSubjectButton(props: { class?: string; electiveId: number; subjectId: number }) {
    const { string } = useI18n()
    const [open, setOpen] = createSignal(false)

    return (
        <>
            <Button icon={AddCircleIcon} class={props.class} size="m" onClick={() => setOpen(true)}>
                {string.ADD_STUDENT_TO_SUBJECT()}
            </Button>
            <Portal>
                <AddStudentToSubjectDialog
                    open={open()}
                    onClose={() => setOpen(false)}
                    electiveId={props.electiveId}
                    subjectId={props.subjectId}
                />
            </Portal>
        </>
    )
}
