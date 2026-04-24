import TrashIcon from '@iconify-icons/mdi/trash-can-outline'
import { createSignal, For, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import { Route } from '../../routes/_adminAuthenticated/manage/subjects/$subjectId'
import { Button } from '../Button'
import AddSubjectToElectiveDialog from '../dialogs/AddSubjectToElectiveDialog'
import RemoveSubjectFromElectiveDialog from '../dialogs/RemoveSubjectFromElectiveDialog'
import { Option, Select } from '../Select'
import { HStack } from '../Stack'
import { useSubjectDisplayContext } from './SubjectDisplayContext'
import type { Elective, Subject } from '../../api'

export default function SubjectAdminEnrollmentActions(props: {
    allElectives: Elective[]
    enrolledElectives: Elective[]
}) {
    const ctx = useSubjectDisplayContext()
    const { string } = useI18n()
    const navigate = Route.useNavigate()
    const [addToElectiveDialogOpen, setAddToElectiveDialogOpen] = createSignal(false)
    const [removeFromElectiveDialogOpen, setRemoveFromElectiveDialogOpen] = createSignal(false)
    const [removalElective, setRemovalElective] = createSignal<Elective | undefined>(undefined)

    const subject = () => ctx.subject as Subject
    const setSearchElectiveId = (electiveId?: string | number) => {
        navigate({
            search: prev => ({
                ...prev,
                elective_id: electiveId !== undefined ? Number(electiveId) : undefined,
            }),
            replace: true,
        })
    }

    return (
        <HStack alignVertical="end">
            <Select
                label={string.ENROLLMENTS()}
                value={ctx.elective?.id}
                onChange={e => {
                    const value = e.currentTarget.value
                    if (value === 'add') {
                        e.currentTarget.value = ''
                        setSearchElectiveId(undefined)
                        setAddToElectiveDialogOpen(true)
                        return
                    }

                    setSearchElectiveId(value)
                }}
            >
                <Option value="" disabled selected>
                    {string.SELECT_ENROLLMENT()}
                </Option>
                <Option value="add">{string.ADD_ELLIPSIS()}</Option>
                <For each={props.enrolledElectives}>
                    {elective => <Option value={elective.id}>{elective.name}</Option>}
                </For>
            </Select>
            <Button
                disabled={!ctx.elective}
                onClick={() => {
                    setRemovalElective(ctx.elective)
                    setRemoveFromElectiveDialogOpen(true)
                }}
                size="m"
                icon={TrashIcon}
                iconType="only"
                variant="tonal-error"
                aria-label={string.REMOVE_SUBJECT_FROM_ENROLLMENT()}
            />

            <Show when={subject()}>
                <Portal>
                    <AddSubjectToElectiveDialog
                        subjectId={subject().id}
                        electives={props.allElectives.filter(e => !props.enrolledElectives.some(el => el.id === e.id))}
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
                            subject={subject()}
                            elective={elective()}
                            open={removeFromElectiveDialogOpen()}
                            onClose={removed => {
                                setRemoveFromElectiveDialogOpen(false)
                                if (removed) {
                                    ctx.setElective(undefined)
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
