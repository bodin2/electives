import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import { useQueryClient } from '@tanstack/solid-query'
import { Icon } from 'm3-solid'
import { createSignal, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { VStack } from '../Stack'
import { ConfirmDialog } from './base/ConfirmDialog'
import type { Elective, Subject } from '../../api'

export default function RemoveSubjectFromElectiveDialog(props: {
    open: boolean
    onClose: (removed: boolean) => unknown
    subject: Subject
    elective: Elective
}) {
    const api = useAPI()
    const qc = useQueryClient()
    const { string } = useI18n()

    const [error, setError] = createSignal<string | null>(null)

    let removed = false

    return (
        <ConfirmDialog
            quick
            variant="danger"
            onCancel={() => props.onClose(removed)}
            onConfirm={async () => {
                try {
                    const subjects = await api.client.electives.fetchSubjects(props.elective.id)
                    await api.client.electives.admin.setSubjects(props.elective.id, [
                        ...subjects.map(it => it.id).filter(id => id !== props.subject.id),
                    ])

                    await api.client.subjects.admin.fetch(props.subject.id, { force: true })
                    await Promise.all([
                        qc.invalidateQueries({ queryKey: ['electives', props.elective.id, 'subjects'] }),
                        qc.invalidateQueries({ queryKey: ['admin', 'subjects', props.subject.id, 'electiveIds'] }),
                    ])

                    removed = true
                    props.onClose(true)
                } catch (e) {
                    console.error(e)
                    setError(String(e))
                }
            }}
            confirmText={string.REMOVE()}
            open={props.open}
            headline={<h1 class="m3-headline-small">{string.REMOVE_SUBJECT_FROM_ENROLLMENT()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={DeleteIcon} />}
            centerHeadline
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
        </ConfirmDialog>
    )
}
