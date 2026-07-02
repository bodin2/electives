import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { useQueryClient } from '@tanstack/solid-query'
import { Icon } from 'm3-solid/src'
import { createSignal, For, Show } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { Option, Select } from '../Select'
import { VStack } from '../Stack'
import type { Enrollment } from '~/api'

export default function AddSubjectToEnrollmentDialog(props: {
    open: boolean
    onClose: (picked: Enrollment | null) => unknown
    subjectId: number
    enrollments: Enrollment[]
}) {
    const api = useAPI()
    const qc = useQueryClient()
    const { string } = useI18n()

    const [enrollment, setEnrollment] = createSignal<Enrollment | null>(null)
    const [error, setError] = createSignal<string | null>(null)

    let form!: HTMLFormElement
    let btn!: HTMLButtonElement

    return (
        <Dialog
            quick
            onClose={() => props.onClose(enrollment())}
            open={props.open}
            headline={<h1 class="m3-headline-small">{string.ADD_SUBJECT_TO_ENROLLMENT()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={AddCircleIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }} ref={form}>
                    <Button
                        variant="text"
                        onClick={() => {
                            setEnrollment(null)
                            form.submit()
                        }}
                    >
                        {string.CANCEL()}
                    </Button>
                    <Button
                        ref={btn}
                        variant="text"
                        onClick={async () => {
                            try {
                                const en = nonNull(enrollment())
                                const subjects = await api.client.enrollments.fetchSubjects(en.id)

                                await api.client.enrollments.admin.setSubjects(en.id, [
                                    ...subjects.map(it => it.id),
                                    props.subjectId,
                                ])
                                await Promise.all([
                                    qc.invalidateQueries({ queryKey: ['enrollments', en.id, 'subjects'] }),
                                    qc.invalidateQueries({
                                        queryKey: ['admin', 'subjects', props.subjectId, 'enrollmentIds'],
                                    }),
                                ])

                                form.submit()
                            } catch (e) {
                                console.error(e)
                                setError(String(e))
                            }
                        }}
                    >
                        {string.ADD()}
                    </Button>
                </form>
            }
        >
            <VStack gap={0} as="form" onSubmit={() => btn.click()}>
                <Select
                    label={string.ENROLLMENTS()}
                    value={enrollment()?.id ?? ''}
                    onInput={e =>
                        setEnrollment(props.enrollments.find(en => en.id === Number(e.currentTarget.value)) || null)
                    }
                >
                    <Option value="" hidden selected>
                        {string.SELECT_ENROLLMENT()}
                    </Option>
                    <For each={props.enrollments.filter(e => !e.subjects?.some(s => s.id === props.subjectId))}>
                        {enrollment => <Option value={enrollment.id}>{enrollment.name}</Option>}
                    </For>
                </Select>
                <Show when={error()}>
                    <p class="text-error m3-body-small">{error()}</p>
                </Show>
            </VStack>
        </Dialog>
    )
}
