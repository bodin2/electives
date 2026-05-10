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
import useEnrollmentOpen from '../../../../hooks/useEnrollmentOpen'
import { useRetryableSubscription } from '../../../../hooks/useRetryableSubscription'
import useSubjectFull from '../../../../hooks/useSubjectFull'
import { useAPI } from '../../../../providers/APIProvider'
import { useEnrollmentCounts } from '../../../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { enrollmentQueryOptions } from '../../../../queries/enrollments'
import { selectionsQueryOptions } from '../../../../queries/selections'
import {
    subjectEnrolledCountQueryOptions,
    subjectMembersQueryOptions,
    subjectQueryOptions,
} from '../../../../queries/subjects'
import { nonNull } from '../../../../utils'
import { formatCountdown } from '../../../../utils/date'
import { catchErrors } from '../../../../utils/error-component'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'
import styles from './$subjectId.module.css'

const log = new Logger('routes/enroll/$subjectId')

export const Route = createFileRoute('/_authenticated/enroll/$enrollmentId/$subjectId')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    params: {
        parse: raw => ({
            enrollmentId: Number(raw.enrollmentId),
            subjectId: Number(raw.subjectId),
        }),
    },
    loader: async ({ context, params }) => {
        const { client, queryClient } = context
        const enrollmentId = params.enrollmentId
        const subjectId = params.subjectId
        const user = nonNull(client.user)

        await Promise.all([
            queryClient.ensureQueryData(
                subjectQueryOptions(client, { subjectId, enrollmentId, withDescription: true }),
            ),
            queryClient.ensureQueryData(enrollmentQueryOptions(client, enrollmentId)),
            user.isStudent() ? queryClient.ensureQueryData(selectionsQueryOptions(client, '@me')) : null,
            queryClient.ensureQueryData(
                subjectMembersQueryOptions(client, { enrollmentId, subjectId, withStudents: false }),
            ),
            queryClient.ensureQueryData(subjectEnrolledCountQueryOptions(client, { enrollmentId, subjectId })),
        ])
    },
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    component: RouteComponent,
})

function RouteComponent() {
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()
    const enrollment = useEnrollmentCounts()
    const user = nonNull(client.user)

    const enrollmentId = () => params().enrollmentId
    const subjectId = () => params().subjectId

    const subjectQuery = createQuery(() =>
        subjectQueryOptions(client, { subjectId: subjectId(), enrollmentId: enrollmentId(), withDescription: true }),
    )
    const enrollmentQuery = createQuery(() => enrollmentQueryOptions(client, enrollmentId()))
    const selectionsQuery = createQuery(() => ({
        ...selectionsQueryOptions(client, '@me'),
        enabled: user.isStudent(),
    }))

    // Teacher is cached separately to student members, so this is fine
    const membersQuery = createQuery(() =>
        subjectMembersQueryOptions(client, {
            enrollmentId: enrollmentId(),
            subjectId: subjectId(),
            withStudents: false,
        }),
    )

    const subject = () => nonNull(subjectQuery.data, 'Subject not fetched')
    const en = () => nonNull(enrollmentQuery.data, 'Enrollment not fetched')
    const teachers = () => membersQuery.data?.teachers ?? []
    const selectedSubject = () => selectionsQuery.data?.get(enrollmentId())

    createEffect(() => {
        enrollment.initializeCounts(enrollmentId(), client.enrollments.resolveAllEnrolledCounts(enrollmentId()))
    })

    // WebSocket subscription for real-time updates
    useRetryableSubscription(
        () => {
            client.gateway.subscribeToEnrollment(
                enrollmentId(),
                [subjectId(), selectedSubject()?.id].filter(Boolean) as number[],
            )
        },
        () => {
            if (client.isGatewayConnected()) {
                client.gateway.subscribeToEnrollment(enrollmentId(), [])
            } else log.warn('WebSocket not connected, skipping unsubscription')
        },
    )

    const isTaughtBy = () => {
        if (!user.isTeacher()) return false
        return teachers().some(t => t.id === user.id)
    }

    const [countdown, setCountdown] = createSignal<number | null>(null)

    const enrollmentOpen = useEnrollmentOpen(en(), {
        onCountdown: timeRemaining => setCountdown(timeRemaining),
    })

    const isFull = useSubjectFull(subject, en)

    const invalidate = () =>
        Promise.all([
            qc.invalidateQueries({ queryKey: ['subjects', subjectId(), 'enrolledCount'] }),
            qc.invalidateQueries({ queryKey: ['subjects', subjectId(), 'members'] }),
            qc.invalidateQueries({ queryKey: ['selections'] }),
            qc.invalidateQueries({ queryKey: ['enrollments', enrollmentId(), 'subjects'] }),
        ])

    const handleStudentRemove = async (stud: User) => {
        const e = nonNull(en())
        await client.selections.delete(stud.id, e.id)
        await invalidate()
    }

    return (
        <Show when={subjectQuery.data && enrollmentQuery.data}>
            <Page name={subject().name}>
                <SubjectInfo
                    subject={subject()}
                    enrollment={en()}
                    teachers={teachers()}
                    user={client.user ?? undefined}
                    onStudentRemove={isTaughtBy() ? handleStudentRemove : undefined}
                    studentRemoveDisabled={user.isTeacher() ? !en().isSelectionOpen() : true}
                    extraActions={props => (
                        <VStack alignHorizontal="center" grow>
                            <Switch>
                                <Match when={user.isStudent() && props.subject.canUserEnroll(user)}>
                                    <DynamicEnrollButton
                                        class={styles.actionButton}
                                        enrollment={en()}
                                        subject={props.subject}
                                        selectedSubject={selectedSubject()}
                                        onInvalidate={invalidate}
                                    />
                                </Match>
                                <Match when={user.isTeacher() && isTaughtBy()}>
                                    <AddStudentToSubjectButton
                                        class={styles.actionButton}
                                        enrollmentId={en().id}
                                        subjectId={props.subject.id}
                                        disabled={isFull() || !en().isSelectionOpen()}
                                    />
                                </Match>
                            </Switch>
                            <Switch>
                                <Match when={!enrollmentOpen()}>
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
