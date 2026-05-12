import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import { createEffect, createSignal, For, on, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../Button'
import AddSubjectToEnrollmentDialog from '../dialogs/AddSubjectToEnrollmentDialog'
import RemoveSubjectFromEnrollmentDialog from '../dialogs/RemoveSubjectFromEnrollmentDialog'
import { Option, Select } from '../Select'
import { HStack } from '../Stack'
import type { Enrollment, Subject } from '~/api'

export default function SubjectAdminEnrollmentActions(props: {
    subject: Subject
    enrollment?: Enrollment
    allEnrollments: Enrollment[]
    addedEnrollments: Enrollment[]
    setEnrollmentId: (id?: number) => void
    onInvalidate: () => Promise<unknown> | unknown
}) {
    const { string } = useI18n()
    const [addToEnrollmentDialogOpen, setAddToEnrollmentDialogOpen] = createSignal(false)
    const [removeFromEnrollmentDialogOpen, setRemoveFromEnrollmentDialogOpen] = createSignal(false)
    const [removalEnrollment, setRemovalEnrollment] = createSignal<Enrollment | undefined>(undefined)

    const setSearchEnrollmentId = async (enrollmentId?: string | number) => {
        props.setEnrollmentId(enrollmentId !== undefined ? Number(enrollmentId) : undefined)
        await props.onInvalidate()
    }

    return (
        <HStack alignVertical="end">
            <SubjectEnrollmentSelector
                enrollment={props.enrollment}
                setEnrollmentId={props.setEnrollmentId}
                addedEnrollments={props.addedEnrollments}
                onAdd={() => setAddToEnrollmentDialogOpen(true)}
            />
            <Button
                disabled={!props.enrollment}
                onClick={() => {
                    setRemovalEnrollment(props.enrollment)
                    setRemoveFromEnrollmentDialogOpen(true)
                }}
                size="m"
                icon={DeleteIcon}
                iconType="only"
                variant="tonal-error"
                aria-label={string.REMOVE_SUBJECT_FROM_ENROLLMENT()}
            />

            <Show when={addToEnrollmentDialogOpen()}>
                <Portal>
                    <AddSubjectToEnrollmentDialog
                        subjectId={props.subject.id}
                        enrollments={props.allEnrollments.filter(
                            e => !props.addedEnrollments.some(en => en.id === e.id),
                        )}
                        open={addToEnrollmentDialogOpen()}
                        onClose={picked => {
                            setAddToEnrollmentDialogOpen(false)
                            if (picked) setSearchEnrollmentId(picked.id)
                        }}
                    />
                </Portal>
            </Show>
            <Show when={removalEnrollment()}>
                {enrollment => (
                    <Portal>
                        <RemoveSubjectFromEnrollmentDialog
                            subject={props.subject}
                            enrollment={enrollment()}
                            open={removeFromEnrollmentDialogOpen()}
                            onClose={removed => {
                                setRemoveFromEnrollmentDialogOpen(false)
                                if (removed) {
                                    setSearchEnrollmentId(undefined)
                                }
                                setRemovalEnrollment(undefined)
                            }}
                        />
                    </Portal>
                )}
            </Show>
        </HStack>
    )
}

export function SubjectEnrollmentSelector(props: {
    enrollment?: Enrollment
    setEnrollmentId: (id?: number) => void
    addedEnrollments: Enrollment[]
    onAdd: () => void
}) {
    const { string } = useI18n()

    createEffect(
        on([() => props.addedEnrollments, () => props.enrollment], ([addedEnrollments, enrollment]) => {
            if (addedEnrollments.length === 1 && !enrollment) {
                props.setEnrollmentId(addedEnrollments[0].id)
            }
        }),
    )

    return (
        <Select
            label={string.ENROLLMENTS()}
            value={props.enrollment?.id ?? ''}
            onInput={e => {
                const value = e.currentTarget.value
                if (value === 'add') {
                    e.currentTarget.value = props.enrollment?.id.toString() ?? ''
                    props.onAdd()
                    return
                }

                props.setEnrollmentId(value ? Number(value) : undefined)
            }}
        >
            <Option value="" hidden selected={!props.enrollment}>
                {string.SELECT_ENROLLMENT()}
            </Option>
            <Option value="add">{string.ADD_ELLIPSIS()}</Option>
            <For each={props.addedEnrollments}>
                {enrollment => (
                    <Option value={enrollment.id} selected={enrollment.id === props.enrollment?.id}>
                        {enrollment.name}
                    </Option>
                )}
            </For>
        </Select>
    )
}
