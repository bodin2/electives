import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import { createEffect, createSignal, For, on, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import AddSubjectToElectiveDialog from '../dialogs/AddSubjectToElectiveDialog'
import RemoveSubjectFromElectiveDialog from '../dialogs/RemoveSubjectFromElectiveDialog'
import { Option, Select } from '../Select'
import { HStack } from '../Stack'
import type { Elective, Subject } from '../../api'

export default function SubjectAdminEnrollmentActions(props: {
    subject: Subject
    elective?: Elective
    allElectives: Elective[]
    addedElectives: Elective[]
    setElectiveId: (id?: number) => void
    onInvalidate: () => Promise<unknown> | unknown
}) {
    const { string } = useI18n()
    const [addToElectiveDialogOpen, setAddToElectiveDialogOpen] = createSignal(false)
    const [removeFromElectiveDialogOpen, setRemoveFromElectiveDialogOpen] = createSignal(false)
    const [removalElective, setRemovalElective] = createSignal<Elective | undefined>(undefined)

    const setSearchElectiveId = async (electiveId?: string | number) => {
        props.setElectiveId(electiveId !== undefined ? Number(electiveId) : undefined)
        await props.onInvalidate()
    }

    return (
        <HStack alignVertical="end">
            <SubjectElectiveSelector
                elective={props.elective}
                setElectiveId={props.setElectiveId}
                addedElectives={props.addedElectives}
                onAdd={() => setAddToElectiveDialogOpen(true)}
            />
            <Button
                disabled={!props.elective}
                onClick={() => {
                    setRemovalElective(props.elective)
                    setRemoveFromElectiveDialogOpen(true)
                }}
                size="m"
                icon={DeleteIcon}
                iconType="only"
                variant="tonal-error"
                aria-label={string.REMOVE_SUBJECT_FROM_ENROLLMENT()}
            />

            <Show when={addToElectiveDialogOpen()}>
                <Portal>
                    <AddSubjectToElectiveDialog
                        subjectId={props.subject.id}
                        electives={props.allElectives.filter(e => !props.addedElectives.some(el => el.id === e.id))}
                        open={addToElectiveDialogOpen()}
                        onClose={picked => {
                            setAddToElectiveDialogOpen(false)
                            if (picked) setSearchElectiveId(picked.id)
                        }}
                    />
                </Portal>
            </Show>
            <Show when={removalElective()}>
                {elective => (
                    <Portal>
                        <RemoveSubjectFromElectiveDialog
                            subject={props.subject}
                            elective={elective()}
                            open={removeFromElectiveDialogOpen()}
                            onClose={removed => {
                                setRemoveFromElectiveDialogOpen(false)
                                if (removed) {
                                    setSearchElectiveId(undefined)
                                }
                                setRemovalElective(undefined)
                            }}
                        />
                    </Portal>
                )}
            </Show>
        </HStack>
    )
}

export function SubjectElectiveSelector(props: {
    elective?: Elective
    setElectiveId: (id?: number) => void
    addedElectives: Elective[]
    onAdd: () => void
}) {
    const { string } = useI18n()

    createEffect(
        on([() => props.addedElectives, () => props.elective], ([addedElectives, elective]) => {
            if (addedElectives.length === 1 && !elective) {
                props.setElectiveId(addedElectives[0].id)
            }
        }),
    )

    return (
        <Select
            label={string.ENROLLMENTS()}
            value={props.elective?.id ?? ''}
            onInput={e => {
                const value = e.currentTarget.value
                if (value === 'add') {
                    e.currentTarget.value = props.elective?.id.toString() ?? ''
                    props.onAdd()
                    return
                }

                props.setElectiveId(value ? Number(value) : undefined)
            }}
        >
            <Option value="" hidden selected={!props.elective}>
                {string.SELECT_ENROLLMENT()}
            </Option>
            <Option value="add">{string.ADD_ELLIPSIS()}</Option>
            <For each={props.addedElectives}>
                {elective => (
                    <Option value={elective.id} selected={elective.id === props.elective?.id}>
                        {elective.name}
                    </Option>
                )}
            </For>
        </Select>
    )
}
