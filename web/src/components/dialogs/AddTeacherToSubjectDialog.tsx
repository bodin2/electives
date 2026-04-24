import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { useRouter } from '@tanstack/solid-router'
import { Icon, TextField } from 'm3-solid'
import { createEffect, createSignal, on, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { debounce } from '../../utils'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { VStack } from '../Stack'
import { SubjectMemberListItem } from '../subjects/SubjectMembersTab'
import type { AdminSubjectPatch, User } from '../../api'

export default function AddTeacherToSubjectDialog(props: {
    open: boolean
    onClose: () => unknown
    subjectId: number
    electiveId: number
    currentTeacherIds: number[]
}) {
    const api = useAPI()
    const router = useRouter()
    const { string } = useI18n()
    const enrolledCounts = useEnrollmentCounts()

    const [teacherId, setTeacherId] = createSignal('')
    const [teacher, setTeacher] = createSignal<User | null>(null)
    const [error, setError] = createSignal<string | null>(null)

    const updateTeacher = debounce(async (id: string) => {
        if (!props.open) return

        if (id.trim() === '') {
            setTeacher(null)
            return
        }

        const realId = Number(id)
        if (Number.isNaN(realId)) {
            setTeacher(null)
            setError(string.ERROR_NUMERIC_VALUE({ field: string.TEACHER_ID() }))
            return
        }

        try {
            const user = await api.client.users.fetch(realId)
            if (!props.open) return

            if (!user.isTeacher()) {
                throw new Error('Not a teacher')
            }
            setTeacher(user)
            setError(null)
        } catch {
            if (!props.open) return
            setTeacher(null)
            setError(string.ERROR_INVALID_CREDENTIALS())
        }
    }, 1000)

    createEffect(on(teacherId, id => updateTeacher(id), { defer: true }))

    let form!: HTMLFormElement
    let btn!: HTMLButtonElement

    return (
        <Dialog
            closedBy="any"
            onClose={props.onClose}
            open={props.open}
            onOpen={() => {
                setTeacherId('')
                setTeacher(null)
                setError(null)
            }}
            headline={<h1 class="m3-headline-small">{string.ADD_TEACHER_TO_SUBJECT()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={AddCircleIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }} ref={form}>
                    <Button variant="text" onClick={() => form.submit()}>
                        {string.CANCEL()}
                    </Button>
                    <Button
                        ref={btn}
                        variant="text"
                        onClick={async () => {
                            await updateTeacher(teacherId())

                            const t = teacher()
                            if (!t) return

                            // Check if already teaching
                            if (props.currentTeacherIds.includes(t.id)) {
                                props.onClose()
                                return
                            }

                            try {
                                const newTeachers = [...props.currentTeacherIds, t.id]

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

                                form.submit()
                            } catch (e) {
                                setError(String(e))
                            }

                            await router.invalidate({
                                sync: true,
                            })
                        }}
                    >
                        {string.ADD_TEACHER_TO_SUBJECT()}
                    </Button>
                </form>
            }
        >
            <VStack
                as="form"
                onSubmit={e => {
                    e.preventDefault()
                    btn.click()
                }}
            >
                <TextField
                    errorIcon
                    error={Boolean(error())}
                    supportingText={error()}
                    label={string.TEACHER_ID()}
                    required
                    onInput={e => setTeacherId(e.currentTarget.value)}
                />
                <Show when={teacher()}>{teach => <SubjectMemberListItem user={teach()} />}</Show>
            </VStack>
        </Dialog>
    )
}
