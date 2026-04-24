import Logger from '@bodin2/electives-common/Logger'
import { useRouter } from '@tanstack/solid-router'
import { Tabs } from 'm3-solid'
import { type Component, createEffect, createSignal, Match, Show, Switch } from 'solid-js'
import { useAutoRefreshResource } from '../../hooks/useAutoRefreshResource'
import { useRetryableSubscription } from '../../hooks/useRetryableSubscription'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { useScrollData } from '../../providers/ScrollDataProvider'
import { nonNull } from '../../utils'
import { VStack } from '../Stack'
import SubjectBottomActions from './SubjectBottomActions'
import SubjectDetailsTab from './SubjectDetailsTab'
import { useSubjectDisplayContext } from './SubjectDisplayContext'
import styles from './SubjectInfo.module.css'
import SubjectMembersTab from './SubjectMembersTab'
import type { Subject } from '../../api'

export interface SubjectInfoProps {
    selectedSubject?: Subject
    extraActions?: Component
}

const log = new Logger('components/subjects/SubjectInfo')

export default function SubjectInfo(props: SubjectInfoProps) {
    const { string } = useI18n()
    const scrollData = useScrollData()
    const api = useAPI()
    const enrollment = useEnrollmentCounts()
    const router = useRouter()
    const ctx = useSubjectDisplayContext()

    const [tab, setTab] = createSignal('info')
    const [outdatedMembers, setOutdatedMembers] = createSignal(false)

    const [membersTabOpened, setMembersTabOpened] = createSignal(false)

    const currentTeacherIds = () => ctx.subject?.teachers.map(t => t.id) ?? []

    createEffect(prev => {
        if (prev !== ctx.elective) setOutdatedMembers(true)
        return ctx.elective
    })

    createEffect(() => {
        setMembersTabOpened(tab() === 'members' && Boolean(ctx.elective))
    })

    createEffect(() => {
        if (ctx.elective) {
            enrollment.initializeCounts(ctx.elective.id, api.client.electives.resolveAllEnrolledCounts(ctx.elective.id))
        }
    })

    useRetryableSubscription(
        () => {
            if (!ctx.elective || !ctx.subject) return
            api.client.gateway.subscribeToElective(
                ctx.elective.id,
                [ctx.subject.id, props.selectedSubject?.id].filter(Boolean) as number[],
            )
        },
        () => {
            if (!ctx.elective) return
            if (api.client.isGatewayConnected()) {
                api.client.gateway.subscribeToElective(ctx.elective.id, [])
            } else log.warn('WebSocket not connected, skipping unsubscription')
        },
    )

    const [members] = useAutoRefreshResource(
        async () => {
            if (!ctx.elective || !ctx.subject)
                return {
                    students: [],
                    teachers: [],
                    capacity: ctx.subject?.capacity ?? 0,
                }

            let { students, teachers } = await api.client.subjects.fetchMembers({
                electiveId: ctx.elective.id,
                subjectId: ctx.subject.id,
                withStudents: true,
            })

            students = [...students].sort((a, b) => a.fullName.localeCompare(b.fullName))
            teachers = [...teachers].sort((a, b) => a.fullName.localeCompare(b.fullName))

            setOutdatedMembers(false)

            return { students, teachers, capacity: ctx.subject.capacity }
        },
        {
            shouldFetch: () => membersTabOpened() || outdatedMembers(),
            getKey: () => (ctx.elective ? `${ctx.elective.id}:${enrollment.getVersion(ctx.elective.id)}` : ''),
            interval: ctx.editable ? 500 : undefined,
        },
    )

    const handleStudentRemove = async (stud: { id: number }) => {
        await api.client.selections.delete(stud.id, nonNull(ctx.elective).id)
        await router.invalidate({ sync: true })
    }

    const handleTeacherRemove = async (teach: { id: number }) => {
        const el = nonNull(ctx.elective)

        await api.client.subjects.admin.patch(nonNull(ctx.subject).id, {
            teachers: currentTeacherIds().filter(id => id !== teach.id),
            electiveId: el.id,
            patchTeachers: true,
            patchCode: false,
            patchDescription: false,
            patchImageUrl: false,
            patchLocation: false,
            patchTeamId: false,
            patchThumbnailUrl: false,
        })

        enrollment.bumpVersion(el.id)
        await router.invalidate({ sync: true })
    }

    return (
        <>
            <Show when={ctx.elective}>
                <Tabs
                    value={tab()}
                    onChange={setTab}
                    class={styles.tabs}
                    style={{
                        'outline-color': scrollData.scrolledVertical ? 'var(--m3c-outline-variant)' : undefined,
                        '--m3-tabs-container-color': scrollData.scrolledVertical
                            ? 'var(--m3c-surface-container)'
                            : undefined,
                    }}
                    tabs={[
                        { label: string.SUBJECT(), value: 'info' },
                        { label: string.MEMBERS_LIST(), value: 'members' },
                    ]}
                />
            </Show>
            <VStack gap={16} grow class={`padded ${styles.tabContent}`}>
                <Switch>
                    <Match when={tab() === 'info' || !ctx.elective}>
                        <SubjectDetailsTab
                            imageClass={styles.image}
                            imagePlaceholderClass={`${styles.image} ${styles.placeholder}`}
                            descriptionClass={`${styles.description} m3-body-large`}
                            labelClass={styles.labelSubText}
                            thumbnailClass={styles.thumbnail}
                        />
                    </Match>
                    <Match when={membersTabOpened()}>
                        <SubjectMembersTab
                            members={members()}
                            onStudentRemove={
                                ctx.editable || (ctx.user?.isTeacher() && ctx.subject?.isTaughtBy(ctx.user))
                                    ? handleStudentRemove
                                    : undefined
                            }
                            onTeacherRemove={ctx.editable ? handleTeacherRemove : undefined}
                            gridClass={styles.membersGrid}
                            headerClass={styles.membersHeader}
                            listClass={styles.membersList}
                            noMembersClass={styles.noMembers}
                        />
                    </Match>
                </Switch>
            </VStack>

            <SubjectBottomActions extraContent={props.extraActions} selectedSubject={props.selectedSubject} />
        </>
    )
}
