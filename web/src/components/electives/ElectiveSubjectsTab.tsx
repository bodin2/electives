import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createSignal, Match, Show, Switch } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { electiveSubjectsQueryOptions } from '../../queries/electives'
import { adminSubjectsQueryOptions } from '../../queries/subjects'
import { Button } from '../Button'
import { HStack, VStack } from '../Stack'
import SubjectList from '../subjects/SubjectList'
import { useElectiveInfoContext } from './ElectiveInfo'
import type { Subject } from '../../api'

export default function ElectiveSubjectsTab(props: { stickyOffset?: number }) {
    const ctx = useElectiveInfoContext()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()

    const [editMode, setEditMode] = createSignal(false)
    const [localSelectedIds, setLocalSelectedIds] = createSignal<number[]>([])
    const [saving, setSaving] = createSignal(false)

    const subjectsQuery = createQuery(() => electiveSubjectsQueryOptions(client, ctx.elective.id))
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
        setLocalSelectedIds([])
    }

    const save = async () => {
        setSaving(true)
        try {
            await client.electives.admin.setSubjects(ctx.elective.id, localSelectedIds())
            await Promise.all([
                qc.invalidateQueries({ queryKey: ['electives', ctx.elective.id] }),
                qc.invalidateQueries({ queryKey: ['admin', 'subjects'] }),
            ])
            setEditMode(false)
        } finally {
            setSaving(false)
        }
    }

    const toggleSubject = (subject: Subject) => {
        setLocalSelectedIds(prev =>
            prev.includes(subject.id) ? prev.filter(id => id !== subject.id) : [...prev, subject.id],
        )
    }

    return (
        <Switch>
            <Match when={editMode()}>
                <Show when={allSubjectsQuery.data}>
                    {allSubjects => (
                        <div style={{ '--sticky-offset': `${props.stickyOffset ?? 48}px` }}>
                            <SubjectList
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
                                noRandom
                                editable
                                selectedIds={localSelectedIds()}
                                onSubjectClick={toggleSubject}
                            />
                        </div>
                    )}
                </Show>
            </Match>
            <Match when={!editMode()}>
                <Show when={subjectsQuery.data}>
                    {data => (
                        <Show
                            when={data().length > 0}
                            fallback={
                                <VStack grow class="padded" style={{ 'padding-top': '8px' }}>
                                    <Button size="m" icon={PlusIcon} onClick={enterEditMode}>
                                        {string.ADD_SUBJECTS()}
                                    </Button>
                                </VStack>
                            }
                        >
                            <div style={{ '--sticky-offset': `${props.stickyOffset ?? 48}px` }}>
                                <SubjectList
                                    noRandom
                                    headerActions={
                                        <Button icon={PlusIcon} onClick={enterEditMode}>
                                            {string.ADD_SUBJECTS()}
                                        </Button>
                                    }
                                    subjects={data()}
                                    elective={ctx.elective}
                                    editable={false}
                                    viewLinkProps={subjectId => ({
                                        to: '/manage/subjects/$subjectId',
                                        params: { subjectId: String(subjectId) },
                                        search: { enrollment_id: ctx.elective.id },
                                    })}
                                />
                            </div>
                        </Show>
                    )}
                </Show>
            </Match>
        </Switch>
    )
}
