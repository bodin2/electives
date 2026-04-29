import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { createSignal, Match, Show, Switch } from 'solid-js'
import { NotFoundError, type User } from '../../../../api'
import AddStudentToSubjectButton from '../../../../components/buttons/AddStudentToSubjectButton'
import DynamicEnrollButton from '../../../../components/buttons/DynamicEnrollButton'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { VStack } from '../../../../components/Stack'
import SubjectInfo from '../../../../components/subjects/SubjectInfo'
import styles from '../../../../components/subjects/SubjectInfo.module.css'
import useElectiveOpen from '../../../../hooks/useElectiveOpen'
import useSubjectFull from '../../../../hooks/useSubjectFull'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { nonNull } from '../../../../utils'
import { formatCountdown } from '../../../../utils/date'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'
import { Route as IndexRoute } from '../../index'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/$subjectId')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
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

        return {
            user,
            subject,
            elective,
            selectedSubject,
        }
    },
    errorComponent: props => {
        if (props.error instanceof NotFoundError) return <NotFoundPage />
        throw props.error
    },
    component: RouteComponent,
})

function RouteComponent() {
    const data = Route.useLoaderData()
    const router = useRouter()
    const { client } = useAPI()
    const { string } = useI18n()

    const [countdown, setCountdown] = createSignal<number | null>(null)

    const electiveOpen = useElectiveOpen(data().elective, {
        onCountdown: timeRemaining => setCountdown(timeRemaining),
    })

    const isFull = useSubjectFull(
        () => data().subject,
        () => data().elective,
    )

    const invalidate = () =>
        router.invalidate({
            filter: r => r.routeId === Route.id,
        })

    const handleStudentRemove = async (stud: User) => {
        const el = nonNull(data().elective)
        await client.selections.delete(stud.id, el.id)
        await invalidate()
    }

    return (
        <Page name={data().subject.name}>
            <SubjectInfo
                subject={data().subject}
                elective={data().elective}
                user={client.user ?? undefined}
                selectedSubject={data().selectedSubject}
                onStudentRemove={data().subject.isTaughtBy(data().user) ? handleStudentRemove : undefined}
                studentRemoveDisabled={data().user.isTeacher() ? !data().elective.isSelectionOpen() : true}
                extraActions={props => (
                    <VStack alignHorizontal="center" grow>
                        <Switch>
                            <Match when={data().user.isStudent() && props.subject.canUserEnroll(data().user)}>
                                <DynamicEnrollButton
                                    class={styles.actionButton}
                                    elective={data().elective}
                                    subject={props.subject}
                                    selectedSubject={data().selectedSubject}
                                    onInvalidate={() =>
                                        router.invalidate({
                                            filter: r => r.routeId === Route.id || r.routeId === IndexRoute.id,
                                            sync: true,
                                        })
                                    }
                                />
                            </Match>
                            <Match when={data().user.isTeacher() && props.subject.isTaughtBy(data().user)}>
                                <AddStudentToSubjectButton
                                    class={styles.actionButton}
                                    electiveId={data().elective.id}
                                    subjectId={props.subject.id}
                                    disabled={isFull() || !data().elective.isSelectionOpen()}
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
    )
}
