import Logger from '@bodin2/electives-common/Logger'
import { useRouter } from '@tanstack/solid-router'
import { type Component, createContext, createEffect, createSignal, Match, Switch, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'
import { useAutoRefreshResource } from '../../hooks/useAutoRefreshResource'
import { useRetryableSubscription } from '../../hooks/useRetryableSubscription'
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
    selectedSubject?: Subject
    extraActions?: Component<{ subject: Subject; elective?: Elective }>
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
    const router = useRouter()

    const [tab, setTab] = createSignal('info')
    const [outdatedMembers, setOutdatedMembers] = createSignal(false)

    const membersTabOpened = () => tab() === 'members'

    const currentTeacherIds = () => props.subject.teachers.map(t => t.id) ?? []

    createEffect(prev => {
        if (prev !== props.elective) setOutdatedMembers(true)
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

    const handleStudentRemove = async (stud: { id: number }) => {
        const el = nonNull(props.elective)

        await api.client.selections.delete(stud.id, el.id)
        enrollment.bumpVersion(el.id)

        await router.invalidate()
    }

    const handleTeacherRemove = async (teach: { id: number }) => {
        const el = nonNull(props.elective)

        await api.client.subjects.admin.patch(nonNull(props.subject).id, {
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

        await router.invalidate()
    }

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
            <StickyTabs
                value={tab()}
                onChange={setTab}
                tabs={[
                    { label: string.SUBJECT(), value: 'info' },
                    { label: string.MEMBERS_LIST(), value: 'members' },
                ]}
            />
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
                            onStudentRemove={
                                props.editable || (props.user?.isTeacher() && props.subject?.isTaughtBy(props.user))
                                    ? handleStudentRemove
                                    : undefined
                            }
                            onTeacherRemove={props.editable ? handleTeacherRemove : undefined}
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
                              const ExtraActions = props.extraActions!
                              return <ExtraActions subject={props.subject} elective={props.elective} />
                          }
                        : undefined
                }
                selectedSubject={props.selectedSubject}
            />
        </SubjectInfoContext.Provider>
    )
}
