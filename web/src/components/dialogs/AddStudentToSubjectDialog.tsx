import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { useRouter } from '@tanstack/solid-router'
import { Icon, TextField } from 'm3-solid'
import { createEffect, createSignal, on, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { debounce } from '../../utils'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { VStack } from '../Stack'
import { SubjectMemberListItem } from '../subjects/SubjectMembersTab'
import type { User } from '../../api'

export default function AddStudentToSubjectDialog(props: {
    open: boolean
    onClose: () => unknown
    electiveId: number
    subjectId: number
}) {
    const api = useAPI()
    const router = useRouter()
    const { string } = useI18n()

    const [studentId, setStudentId] = createSignal('')
    const [student, setStudent] = createSignal<User | null>(null)
    const [error, setError] = createSignal<string | null>(null)

    const updateStudent = debounce(async (id: string) => {
        if (id.trim() === '') {
            setStudent(null)
            return
        }

        const realId = Number(id)
        if (Number.isNaN(realId)) {
            setStudent(null)
            setError(string.ERROR_STUDENT_ID_NUMERIC())
            return
        }

        try {
            const user = await api.client.users.fetch(realId)
            if (!user.isStudent()) {
                throw new Error('Not a student')
            }
            setStudent(user)
            setError(null)
        } catch {
            setStudent(null)
            setError(string.ERROR_INVALID_CREDENTIALS())
        }
    }, 1000)

    createEffect(on(studentId, id => updateStudent(id), { defer: true }))

    let btn!: HTMLButtonElement

    return (
        <Dialog
            closedBy="any"
            onClose={props.onClose}
            open={props.open}
            headline={<h1 class="m3-headline-small">{string.ADD_STUDENT_TO_SUBJECT()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={AddCircleIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }}>
                    <Button variant="text" onClick={() => props.onClose()}>
                        {string.CANCEL()}
                    </Button>
                    <Button
                        ref={btn}
                        variant="text"
                        onClick={async () => {
                            await updateStudent(studentId())

                            const stud = student()
                            if (!stud) return

                            try {
                                await api.client.selections.set(stud.id, props.electiveId, props.subjectId)

                                props.onClose()
                            } catch (e) {
                                setError(String(e))
                            }

                            await router.invalidate({
                                sync: true,
                            })
                        }}
                    >
                        {string.ADD_STUDENT_TO_SUBJECT()}
                    </Button>
                </form>
            }
        >
            <VStack as="form" onSubmit={() => btn.click()}>
                <TextField
                    errorIcon
                    error={Boolean(error())}
                    supportingText={error()}
                    label={string.STUDENT_ID()}
                    required
                    ref={studentId}
                    onInput={e => setStudentId(e.currentTarget.value)}
                />
                <Show when={student()}>{stud => <SubjectMemberListItem user={stud()} />}</Show>
            </VStack>
        </Dialog>
    )
}
