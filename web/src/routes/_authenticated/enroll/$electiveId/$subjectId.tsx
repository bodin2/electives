import Logger from '@bodin2/electives-common/Logger'
import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createEffect, createSignal, Match, Show, Switch } from 'solid-js'
import { NotFoundError, type User } from '../../../../api'
import AddStudentToSubjectButton from '../../../../components/buttons/AddStudentToSubjectButton'
import DynamicEnrollButton from '../../../../components/buttons/DynamicEnrollButton'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { VStack } from '../../../../components/Stack'
import SubjectInfo from '../../../../components/subjects/SubjectInfo'
import useElectiveOpen from '../../../../hooks/useElectiveOpen'
import { useRetryableSubscription } from '../../../../hooks/useRetryableSubscription'
import useSubjectFull from '../../../../hooks/useSubjectFull'
import { useAPI } from '../../../../providers/APIProvider'
import { useEnrollmentCounts } from '../../../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { electiveQueryOptions } from '../../../../queries/electives'
import { selectionsQueryOptions } from '../../../../queries/selections'
import {
    subjectEnrolledCountQueryOptions,
    subjectMembersQueryOptions,
    subjectQueryOptions,
} from '../../../../queries/subjects'
import { nonNull } from '../../../../utils'
import { formatCountdown } from '../../../../utils/date'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'
import styles from './$subjectId.module.css'

const log = new Logger('routes/enroll/$subjectId')

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/$subjectId')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    params: {
        parse: raw => ({
            electiveId: Number(raw.electiveId),
            subjectId: Number(raw.subjectId),
        }),
    },
    loader: async ({ context, params }) => {
        const { client, queryClient } = context
        const electiveId = params.electiveId
        const subjectId = params.subjectId
        const user = nonNull(client.user)

        await Promise.all([
            queryClient.ensureQueryData(subjectQueryOptions(client, { subjectId, electiveId, withDescription: true })),
            queryClient.ensureQueryData(electiveQueryOptions(client, electiveId)),
            user.isStudent() ? queryClient.ensureQueryData(selectionsQueryOptions(client, '@me')) : null,
            queryClient.ensureQueryData(
                subjectMembersQueryOptions(client, { electiveId, subjectId, withStudents: false }),
            ),
            queryClient.ensureQueryData(subjectEnrolledCountQueryOptions(client, { electiveId, subjectId })),
        ])
    },
    errorComponent: props => {
        if (props.error instanceof NotFoundError) return <NotFoundPage />
        throw props.error
    },
    component: RouteComponent,
})

function RouteComponent() {
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()
    const enrollment = useEnrollmentCounts()
    const user = nonNull(client.user)

    const electiveId = () => params().electiveId
    const subjectId = () => params().subjectId

    const subjectQuery = createQuery(() =>
        subjectQueryOptions(client, { subjectId: subjectId(), electiveId: electiveId(), withDescription: true }),
    )
    const electiveQuery = createQuery(() => electiveQueryOptions(client, electiveId()))
    const selectionsQuery = createQuery(() => ({
        ...selectionsQueryOptions(client, '@me'),
        enabled: user.isStudent(),
    }))

    // Teacher is cached separately to student members, so this is fine
    const membersQuery = createQuery(() =>
        subjectMembersQueryOptions(client, {
            electiveId: electiveId(),
            subjectId: subjectId(),
            withStudents: false,
        }),
    )

    const subject = () => nonNull(subjectQuery.data, 'Subject not fetched')
    const elective = () => nonNull(electiveQuery.data, 'Elective not fetched')
    const teachers = () => membersQuery.data?.teachers ?? []
    const selectedSubject = () => selectionsQuery.data?.get(electiveId())

    createEffect(() => {
        enrollment.initializeCounts(electiveId(), client.electives.resolveAllEnrolledCounts(electiveId()))
    })

    // WebSocket subscription for real-time updates
    useRetryableSubscription(
        () => {
            client.gateway.subscribeToElective(
                electiveId(),
                [subjectId(), selectedSubject()?.id].filter(Boolean) as number[],
            )
        },
        () => {
            if (client.isGatewayConnected()) {
                client.gateway.subscribeToElective(electiveId(), [])
            } else log.warn('WebSocket not connected, skipping unsubscription')
        },
    )

    const isTaughtBy = () => {
        if (!user.isTeacher()) return false
        return teachers().some(t => t.id === user.id)
    }

    const [countdown, setCountdown] = createSignal<number | null>(null)

    const electiveOpen = useElectiveOpen(elective(), {
        onCountdown: timeRemaining => setCountdown(timeRemaining),
    })

    const isFull = useSubjectFull(subject, elective)

    const invalidate = () =>
        Promise.all([
            qc.invalidateQueries({ queryKey: ['subjects', subjectId(), 'enrolledCount'] }),
            qc.invalidateQueries({ queryKey: ['subjects', subjectId(), 'members'] }),
            qc.invalidateQueries({ queryKey: ['selections'] }),
            qc.invalidateQueries({ queryKey: ['electives', electiveId(), 'subjects'] }),
        ])

    const handleStudentRemove = async (stud: User) => {
        const el = nonNull(elective())
        await client.selections.delete(stud.id, el.id)
        await invalidate()
    }

    return (
        <Show when={subjectQuery.data && electiveQuery.data}>
            <Page name={subject().name}>
                <SubjectInfo
                    subject={subject()}
                    elective={elective()}
                    teachers={teachers()}
                    user={client.user ?? undefined}
                    onStudentRemove={isTaughtBy() ? handleStudentRemove : undefined}
                    studentRemoveDisabled={user.isTeacher() ? !elective().isSelectionOpen() : true}
                    extraActions={props => (
                        <VStack alignHorizontal="center" grow>
                            <Switch>
                                <Match when={user.isStudent() && props.subject.canUserEnroll(user)}>
                                    <DynamicEnrollButton
                                        class={styles.actionButton}
                                        elective={elective()}
                                        subject={props.subject}
                                        selectedSubject={selectedSubject()}
                                        onInvalidate={invalidate}
                                    />
                                </Match>
                                <Match when={user.isTeacher() && isTaughtBy()}>
                                    <AddStudentToSubjectButton
                                        class={styles.actionButton}
                                        electiveId={elective().id}
                                        subjectId={props.subject.id}
                                        disabled={isFull() || !elective().isSelectionOpen()}
                                    />
                                </Match>
                            </Switch>
                            <Switch>
                                <Match when={!electiveOpen()}>
                                    <Show
                                        when={formatCountdown(countdown())}
                                        fallback={<p class="m3-body-small text-error">{string.ENROLLMENT_CLOSED()}</p>}
                                    >
                                        {time => (
                                            <p class="m3-body-small text-error">
                                                {string.ENROLLMENT_CLOSED_OPENING_IN({ time: time() })}
                                            </p>
                                        )}
                                    </Show>
                                </Match>
                                <Match when={isFull()}>
                                    <p class="m3-body-small text-error">{string.SUBJECT_FULL_HINT()}</p>
                                </Match>
                            </Switch>
                        </VStack>
                    )}
                />
            </Page>
        </Show>
    )
}
