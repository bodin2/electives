import Logger from '@bodin2/electives-common/Logger'
import { type Component, createContext, createEffect, createSignal, Match, Show, Switch, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'
import { useAutoRefreshResource } from '../../hooks/useAutoRefreshResource'
import { useRetryableSubscription } from '../../hooks/useRetryableSubscription'
import { useTabPersistence } from '../../hooks/useTabPersistence'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { VStack } from '../Stack'
import StickyTabs from '../StickyTabs'
import SubjectBottomActions from './SubjectBottomActions'
import SubjectDetailsTab from './SubjectDetailsTab'
import styles from './SubjectInfo.module.css'
import SubjectMembersTab from './SubjectMembersTab'
import type { Elective, Subject, User } from '../../api'
import type { PatchSetterKey } from './SubjectDisplayContext'

export interface SubjectInfoProps {
    subject: Subject
    elective?: Elective
    user?: User
    editable?: boolean
    onEdit?: (field: string, value: any, patchKey?: PatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onStudentRemove?: (student: User) => Promise<void> | void
    onTeacherRemove?: (teacher: User) => Promise<void> | void
    selectedSubject?: Subject
    creating?: boolean
    extraActions?: Component<{ subject: Subject; elective?: Elective }>
    persistTab?: boolean
}

interface SubjectInfoContext {
    subject: Subject
    elective?: Elective
    user?: User
    editable?: boolean
    onEdit?: (field: string, value: any, patchKey?: PatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
}

const SubjectInfoContext = createContext<SubjectInfoContext>(null as unknown as SubjectInfoContext)
export const useSubjectInfoContext = () =>
    nonNull(useContext(SubjectInfoContext), 'useSubjectInfoContext must be used within a SubjectInfo provider')

const log = new Logger('components/subjects/SubjectInfo')

export default function SubjectInfo(props: SubjectInfoProps) {
    const { string } = useI18n()
    const api = useAPI()
    const enrollment = useEnrollmentCounts()

    const [tab, setTab] = createSignal('info')
    useTabPersistence(tab, setTab, { disabled: props.persistTab === false })
    const [outdatedMembers, setOutdatedMembers] = createSignal(false)

    const membersTabOpened = () => tab() === 'members'

    createEffect(prev => {
        if (prev !== undefined && prev !== props.elective) setOutdatedMembers(true)
        return props.elective
    })

    createEffect(() => {
        if (props.elective) {
            enrollment.initializeCounts(
                props.elective.id,
                api.client.electives.resolveAllEnrolledCounts(props.elective.id),
            )
        }
    })

    useRetryableSubscription(
        () => {
            if (!props.elective || !props.subject || props.editable) return
            api.client.gateway.subscribeToElective(
                props.elective.id,
                [props.subject.id, props.selectedSubject?.id].filter(Boolean) as number[],
            )
        },
        () => {
            if (!props.elective || props.editable) return
            if (api.client.isGatewayConnected()) {
                api.client.gateway.subscribeToElective(props.elective.id, [])
            } else log.warn('WebSocket not connected, skipping unsubscription')
        },
    )

    const [members] = useAutoRefreshResource(
        async () => {
            if (!props.elective || !props.subject) return undefined

            let { students, teachers } = await api.client.subjects.fetchMembers({
                electiveId: props.elective.id,
                subjectId: props.subject.id,
                withStudents: true,
            })

            students = [...students].sort((a, b) => a.fullName.localeCompare(b.fullName))
            teachers = [...teachers].sort((a, b) => a.fullName.localeCompare(b.fullName))

            setOutdatedMembers(false)

            return { students, teachers, capacity: props.subject.capacity }
        },
        {
            shouldFetch: () => membersTabOpened() || outdatedMembers(),
            getKey: () => (props.elective ? `${props.elective.id}:${enrollment.getVersion(props.elective.id)}` : ''),
            interval: props.editable ? 500 : undefined,
        },
    )

    // SolidJS moment
    const [info, setInfo] = createStore<SubjectInfoContext>(null as unknown as SubjectInfoContext)
    createEffect(() => {
        setInfo({
            subject: props.subject,
            elective: props.elective,
            user: props.user,
            editable: props.editable,
            onEdit: props.onEdit,
            onSave: props.onSave,
        })
    })

    return (
        <SubjectInfoContext.Provider value={info}>
            <Show when={!props.creating}>
                <StickyTabs
                    value={tab()}
                    onChange={setTab}
                    tabs={[
                        { label: string.SUBJECT(), value: 'info' },
                        { label: string.MEMBERS_LIST(), value: 'members' },
                    ]}
                />
            </Show>
            <VStack gap={16} grow class={`padded ${styles.tabContent}`}>
                <Switch>
                    <Match when={tab() === 'info'}>
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
                            onStudentRemove={props.onStudentRemove}
                            onTeacherRemove={props.onTeacherRemove}
                            gridClass={styles.membersGrid}
                            headerClass={styles.membersHeader}
                            listClass={styles.membersList}
                            noMembersClass={styles.noMembers}
                        />
                    </Match>
                </Switch>
            </VStack>

            <SubjectBottomActions
                extraContent={
                    props.extraActions
                        ? () => {
                              const ExtraActions = nonNull(props.extraActions)
                              return <ExtraActions subject={props.subject} elective={props.elective} />
                          }
                        : undefined
                }
            />
        </SubjectInfoContext.Provider>
    )
}
