import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { useRouter } from '@tanstack/solid-router'
import { Icon } from 'm3-solid'
import { createSignal, For, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { Option, Select } from '../Select'
import { VStack } from '../Stack'
import type { Elective } from '../../api'

export default function AddSubjectToElectiveDialog(props: {
    open: boolean
    onClose: (picked: Elective | null) => unknown
    subjectId: number
    electives: Elective[]
}) {
    const api = useAPI()
    const router = useRouter()
    const { string } = useI18n()

    const [elective, setElective] = createSignal<Elective | null>(null)
    const [error, setError] = createSignal<string | null>(null)

    let form!: HTMLFormElement
    let btn!: HTMLButtonElement

    return (
        <Dialog
            quick
            onClose={() => props.onClose(elective())}
            open={props.open}
            headline={<h1 class="m3-headline-small">{string.ADD_SUBJECT_TO_ENROLLMENT()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={AddCircleIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }} ref={form}>
                    <Button
                        variant="text"
                        onClick={() => {
                            setElective(null)
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
                                const el = nonNull(elective())
                                const subjects = await api.client.electives.fetchSubjects(el.id)

                                await api.client.electives.admin.setSubjects(el.id, [
                                    ...subjects.map(it => it.id),
                                    props.subjectId,
                                ])
                                await api.client.subjects.admin.fetch(props.subjectId, { force: true })
                                await router.invalidate()

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
                    value={elective()?.id ?? ''}
                    onChange={e =>
                        setElective(props.electives.find(el => el.id === Number(e.currentTarget.value)) || null)
                    }
                >
                    <Option value="" disabled selected>
                        {string.SELECT_ENROLLMENT()}
                    </Option>
                    <For each={props.electives.filter(e => !e.subjects?.some(s => s.id === props.subjectId))}>
                        {elective => <Option value={elective.id}>{elective.name}</Option>}
                    </For>
                </Select>
                <Show when={error()}>
                    <p class="text-error m3-body-small">{error()}</p>
                </Show>
            </VStack>
        </Dialog>
    )
}
