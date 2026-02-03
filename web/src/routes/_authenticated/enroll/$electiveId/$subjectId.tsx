import Logger from '@bodin2/electives-common/Logger'
import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { Tabs } from 'm3-solid'
import { createEffect, createSignal, Match, Show, Switch } from 'solid-js'
import { NotFoundError } from '../../../../api'
import AddStudentToSubjectButton from '../../../../components/buttons/AddStudentToSubjectButton'
import DynamicEnrollButton from '../../../../components/buttons/DynamicEnrollButton'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { VStack } from '../../../../components/Stack'
import SubjectDetailsTab from '../../../../components/subjects/SubjectDetailsTab'
import SubjectMembersTab from '../../../../components/subjects/SubjectMembersTab'
import { useAutoRefreshResource } from '../../../../hooks/useAutoRefreshResource'
import { useRetryableSubscription } from '../../../../hooks/useRetryableSubscription'
import { useAPI } from '../../../../providers/APIProvider'
import { useEnrollmentCounts } from '../../../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { useScrollData } from '../../../../providers/ScrollDataProvider'
import { nonNull } from '../../../../utils'
import styles from './$subjectId.module.css'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/$subjectId')({
    params: {
        parse: raw => ({
            electiveId: Number(raw.electiveId),
            subjectId: Number(raw.subjectId),
        }),
    },
    loader: async ({ context, params }) => {
        const electiveId = params.electiveId
        const subjectId = params.subjectId
        const user = nonNull(context.client.user)

        const [subject, elective, selections] = await Promise.all([
            context.client.subjects.fetch({
                subjectId,
                electiveId,
                withDescription: true,
            }),
            context.client.electives.fetch(electiveId),
            user.isStudent() ? context.client.selections.fetch('@me') : null,
        ])

        const selectedSubject = selections?.get(electiveId)
        const initialEnrolledCounts = context.client.electives.resolveAllEnrolledCounts(electiveId)

        return {
            user,
            subject,
            elective,
            selectedSubject,
            initialEnrolledCounts,
        }
    },
    errorComponent: props => {
        if (props.error instanceof NotFoundError) return <NotFoundPage />
        throw props.error
    },
    component: RouteComponent,
})

const log = new Logger('routes/$electiveId/$subjectId')

function RouteComponent() {
    const { string } = useI18n()
    const scrollData = useScrollData()
    const data = Route.useLoaderData()
    const api = useAPI()
    const enrollment = useEnrollmentCounts()
    const router = useRouter()

    const [tab, setTab] = createSignal('info')

    const [membersTabOpened, setMembersTabOpened] = createSignal(false)

    createEffect(() => {
        if (tab() === 'members') {
            setMembersTabOpened(true)
        }
    })

    createEffect(() => {
        enrollment.initializeCounts(data().elective.id, data().initialEnrolledCounts)
    })

    useRetryableSubscription(
        () => {
            api.client.gateway.subscribeToElective(
                data().elective.id,
                [data().subject.id, data().selectedSubject?.id].filter(Boolean) as number[],
            )
        },
        () => {
            if (api.client.isGatewayConnected()) {
                api.client.gateway.subscribeToElective(data().elective.id, [])
            } else log.warn('WebSocket not connected, skipping unsubscription')
        },
    )

    const [members] = useAutoRefreshResource(
        async () => {
            let { students, teachers } = await api.client.subjects.fetchMembers({
                electiveId: data().elective.id,
                subjectId: data().subject.id,
                withStudents: true,
            })

            students = [...students].sort((a, b) => a.fullName.localeCompare(b.fullName))
            teachers = [...teachers].sort((a, b) => a.fullName.localeCompare(b.fullName))

            return { students, teachers }
        },
        {
            shouldFetch: membersTabOpened,
            getVersion: () => enrollment.getVersion(data().elective.id),
        },
    )

    return (
        <Page name={data().subject.name}>
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
                            subject={data().subject}
                            electiveId={data().elective.id}
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
                                data().user.isTeacher() && data().subject.isUserTeaching(data().user)
                                    ? async stud => {
                                          await api.client.selections.delete(stud.id, data().elective.id)
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
                        data().user.isStudent() &&
                        (data().subject.teamId == null || data().subject.canUserEnroll(data().user))
                    }
                >
                    <DynamicEnrollButton
                        class={styles.actionButton}
                        elective={data().elective}
                        subject={data().subject}
                        selectedSubject={data().selectedSubject}
                    />
                </Show>
                <Show when={data().user.isTeacher() && data().subject.isUserTeaching(data().user)}>
                    <AddStudentToSubjectButton
                        class={styles.actionButton}
                        electiveId={data().elective.id}
                        subjectId={data().subject.id}
                    />
                </Show>
            </VStack>
        </Page>
    )
}
