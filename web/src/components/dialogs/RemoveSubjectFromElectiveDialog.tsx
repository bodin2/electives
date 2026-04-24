import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import { useRouter } from '@tanstack/solid-router'
import { Icon } from 'm3-solid'
import { createSignal, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { VStack } from '../Stack'
import type { Elective, Subject } from '../../api'

export default function RemoveSubjectFromElectiveDialog(props: {
    open: boolean
    onClose: (removed: boolean) => unknown
    subject: Subject
    elective: Elective
}) {
    const api = useAPI()
    const router = useRouter()
    const { string } = useI18n()

    const [error, setError] = createSignal<string | null>(null)

    let removed = false
    let form!: HTMLFormElement
    let btn!: HTMLButtonElement

    return (
        <Dialog
            quick
            onClose={() => props.onClose(removed)}
            open={props.open}
            headline={<h1 class="m3-headline-small">{string.REMOVE_SUBJECT_FROM_ENROLLMENT()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={DeleteIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }} ref={form}>
                    <Button variant="text" onClick={() => form.submit()}>
                        {string.CANCEL()}
                    </Button>
                    <Button
                        ref={btn}
                        variant="tonal-error"
                        onClick={async () => {
                            try {
                                const subjects = await api.client.electives.fetchSubjects(props.elective.id)
                                await api.client.electives.admin.setSubjects(props.elective.id, [
                                    ...subjects.map(it => it.id).filter(id => id !== props.subject.id),
                                ])

                                await api.client.subjects.admin.fetch(props.subject.id, { force: true })
                                await router.invalidate({ sync: true })

                                removed = true

                                form.submit()
                            } catch (e) {
                                console.error(e)
                                setError(String(e))
                            }
                        }}
                    >
                        {string.REMOVE()}
                    </Button>
                </form>
            }
        >
            <VStack>
                <p>
                    {string.REMOVE_SUBJECT_FROM_ENROLLMENT_DESCRIPTION({
                        subjectName: <strong>{props.subject.name}</strong>,
                        electiveName: <strong>{props.elective.name}</strong>,
                    })}
                </p>
                <Show when={error()}>
                    <p class="text-error m3-body-small">{error()}</p>
                </Show>
            </VStack>
        </Dialog>
    )
}
