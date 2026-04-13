import Logger from '@bodin2/electives-common/Logger'
import { useRouter } from '@tanstack/solid-router'
import { Tabs } from 'm3-solid'
import { createEffect, createSignal, Match, Show, Switch } from 'solid-js'
import { useAutoRefreshResource } from '../../hooks/useAutoRefreshResource'
import { useRetryableSubscription } from '../../hooks/useRetryableSubscription'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { useScrollData } from '../../providers/ScrollDataProvider'
import AddStudentToSubjectButton from '../buttons/AddStudentToSubjectButton'
import DynamicEnrollButton from '../buttons/DynamicEnrollButton'
import { VStack } from '../Stack'
import SubjectDetailsTab from './SubjectDetailsTab'
import styles from './SubjectInfo.module.css'
import SubjectMembersTab from './SubjectMembersTab'
import type { Elective, Subject, User } from '../../api'

export interface SubjectInfoProps {
    user: User
    subject: Subject
    elective: Elective
    selectedSubject: Subject | undefined
    initialEnrolledCounts: Record<number, number>
}

const log = new Logger('components/subjects/SubjectInfo')

export default function SubjectInfo(props: SubjectInfoProps) {
    const { string } = useI18n()
    const scrollData = useScrollData()
    const api = useAPI()
    const enrollment = useEnrollmentCounts()
    const router = useRouter()

    const [tab, setTab] = createSignal('info')

    const [membersTabOpened, setMembersTabOpened] = createSignal(false)

    createEffect(() => {
        setMembersTabOpened(tab() === 'members')
    })

    createEffect(() => {
        enrollment.initializeCounts(props.elective.id, props.initialEnrolledCounts)
    })

    useRetryableSubscription(
        () => {
            api.client.gateway.subscribeToElective(
                props.elective.id,
                [props.subject.id, props.selectedSubject?.id].filter(Boolean) as number[],
            )
        },
        () => {
            if (api.client.isGatewayConnected()) {
                api.client.gateway.subscribeToElective(props.elective.id, [])
            } else log.warn('WebSocket not connected, skipping unsubscription')
        },
    )

    const [members] = useAutoRefreshResource(
        async () => {
            let { students, teachers } = await api.client.subjects.fetchMembers({
                electiveId: props.elective.id,
                subjectId: props.subject.id,
                withStudents: true,
            })

            students = [...students].sort((a, b) => a.fullName.localeCompare(b.fullName))
            teachers = [...teachers].sort((a, b) => a.fullName.localeCompare(b.fullName))

            return { students, teachers, maxStudents: props.subject.capacity }
        },
        {
            shouldFetch: membersTabOpened,
            getVersion: () => enrollment.getVersion(props.elective.id),
        },
    )

    return (
        <>
            <Tabs
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
            <VStack gap={16} class={`padded ${styles.tabContent}`}>
                <Switch>
                    <Match when={tab() === 'info'}>
                        <SubjectDetailsTab
                            subject={props.subject}
                            electiveId={props.elective.id}
                            imageClass={styles.image}
                            imagePlaceholderClass={`${styles.image} ${styles.placeholder}`}
                            descriptionClass={`${styles.description} m3-body-large`}
                            labelClass={styles.labelSubText}
                        />
                    </Match>
                    <Match when={tab() === 'members'}>
                        <SubjectMembersTab
                            members={members()}
                            onStudentRemove={
                                props.user.isTeacher() && props.subject.isUserTeaching(props.user)
                                    ? async stud => {
                                          await api.client.selections.delete(stud.id, props.elective.id)
                                          await router.invalidate({
                                              sync: true,
                                          })
                                      }
                                    : undefined
                            }
                            gridClass={styles.membersGrid}
                            headerClass={styles.membersHeader}
                            listClass={styles.membersList}
                            noMembersClass={styles.noMembers}
                        />
                    </Match>
                </Switch>
            </VStack>
            <VStack
                alignHorizontal="center"
                class={styles.enrollButtonContainer}
                style={{
                    'outline-color':
                        scrollData.maxScrollY - scrollData.scrollY > 16 ? 'var(--m3c-outline-variant)' : undefined,
                }}
            >
                <Show
                    when={
                        props.user.isStudent() &&
                        (props.subject.teamId == null || props.subject.canUserEnroll(props.user))
                    }
                >
                    <DynamicEnrollButton
                        class={styles.actionButton}
                        elective={props.elective}
                        subject={props.subject}
                        selectedSubject={props.selectedSubject}
                    />
                </Show>
                <Show when={props.user.isTeacher() && props.subject.isUserTeaching(props.user)}>
                    <AddStudentToSubjectButton
                        class={styles.actionButton}
                        electiveId={props.elective.id}
                        subjectId={props.subject.id}
                    />
                </Show>
            </VStack>
        </>
    )
}
