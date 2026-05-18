import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createSignal, Match, Show, Switch } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentSubjectsQueryOptions } from '~/queries/enrollments'
import { adminSubjectsQueryOptions } from '~/queries/subjects'
import { Button } from '../Button'
import { HStack, VStack } from '../Stack'
import SubjectList from '../subjects/SubjectList'
import { useEnrollmentInfoContext } from './EnrollmentInfo'
import type { Subject } from '~/api'

export default function EnrollmentSubjectsTab(props: { stickyOffset?: number }) {
    const ctx = useEnrollmentInfoContext()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()

    const [editMode, setEditMode] = createSignal(false)
    const [localSelectedIds, setLocalSelectedIds] = createSignal<number[]>([])
    const [saving, setSaving] = createSignal(false)
    const [showRemovalWarning, setShowRemovalWarning] = createSignal(false)

    const subjectsQuery = createQuery(() => enrollmentSubjectsQueryOptions(client, ctx.enrollment.id))
    const allSubjectsQuery = createQuery(() => ({
        ...adminSubjectsQueryOptions(client),
        enabled: editMode(),
    }))

    const enterEditMode = () => {
        setLocalSelectedIds((subjectsQuery.data ?? []).map(s => s.id))
        setEditMode(true)
    }

    const discard = () => {
        setEditMode(false)
        setShowRemovalWarning(false)
        setLocalSelectedIds([])
    }

    const save = async () => {
        setSaving(true)
        try {
            await client.enrollments.admin.setSubjects(ctx.enrollment.id, localSelectedIds())
            await Promise.all([
                qc.invalidateQueries({ queryKey: ['enrollments', ctx.enrollment.id, 'subjects'] }),
                qc.invalidateQueries({
                    predicate: ({ queryKey: [first, second, _, fourth] }) =>
                        first === 'admin' && second === 'subjects' && fourth === 'enrollmentIds',
                }),
            ])
            setEditMode(false)
        } finally {
            setSaving(false)
        }
    }

    const toggleSubject = (subject: Subject) => {
        const deselecting = localSelectedIds().includes(subject.id)
        if (deselecting) setShowRemovalWarning(true)

        setLocalSelectedIds(prev => (deselecting ? prev.filter(id => id !== subject.id) : [...prev, subject.id]))
    }

    return (
        <Switch>
            <Match when={editMode() && allSubjectsQuery.data}>
                {allSubjects => (
                    <div style={{ '--sticky-offset': `${props.stickyOffset ?? 48}px` }}>
                        <SubjectList
                            elementAboveGrid={
                                <Show when={showRemovalWarning()}>
                                    <p class="padded text-error">{string.SUBJECT_ENROLLMENT_REMOVAL_WARNING()}</p>
                                </Show>
                            }
                            headerActions={
                                <HStack gap={8}>
                                    <Button variant="text" onClick={discard}>
                                        {string.DISCARD()}
                                    </Button>
                                    <Button loading={saving()} onClick={save}>
                                        {string.SAVE()}
                                    </Button>
                                </HStack>
                            }
                            subjects={allSubjects()}
                            enrollment={ctx.enrollment}
                            noRandom
                            editable
                            selectedIds={localSelectedIds()}
                            onSubjectClick={toggleSubject}
                        />
                    </div>
                )}
            </Match>
            <Match when={!editMode() && subjectsQuery.data}>
                {data => (
                    <Show
                        when={data().length > 0}
                        fallback={
                            <VStack grow class="padded" style={{ 'padding-top': '8px' }}>
                                <Button size="m" icon={PlusIcon} onClick={enterEditMode}>
                                    {string.ADD_SUBJECTS_TO_ENROLLMENT()}
                                </Button>
                            </VStack>
                        }
                    >
                        <div style={{ '--sticky-offset': `${props.stickyOffset ?? 48}px` }}>
                            <SubjectList
                                noRandom
                                headerActions={
                                    <Button icon={PencilOutlineIcon} onClick={enterEditMode}>
                                        {string.ADD_OR_REMOVE_SUBJECTS()}
                                    </Button>
                                }
                                subjects={data()}
                                enrollment={ctx.enrollment}
                                editable={false}
                                viewLinkProps={subjectId => ({
                                    to: '/manage/subjects/$subjectId',
                                    params: { subjectId: String(subjectId) },
                                    search: { enrollment_id: ctx.enrollment.id },
                                })}
                            />
                        </div>
                    </Show>
                )}
            </Match>
        </Switch>
    )
}
