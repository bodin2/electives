import BookIcon from '@iconify-icons/mdi/book'
import TicketIcon from '@iconify-icons/mdi/ticket'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { Match, Show, Switch } from 'solid-js'
import { NotFoundError, UnauthorizedError } from '~/api'
import { UserType } from '~/api/types'
import IconLabel from '~/components/IconLabel'
import Page from '~/components/Page'
import { SuspenseLoadingPage } from '~/components/pages/LoadingPage'
import NotFoundPage from '~/components/pages/NotFoundPage'
import { VStack } from '~/components/Stack'
import StudentSelectionsTab from '~/components/users/StudentSelectionsTab'
import TeacherSubjectsTab from '~/components/users/TeacherSubjectsTab'
import { UserInfoContextProvider } from '~/components/users/UserInfo'
import { UserProfile } from '~/components/users/UserProfile'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { selectionsQueryOptions } from '~/queries/selections'
import { teacherSubjectsQueryOptions, userQueryOptions } from '~/queries/users'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '~/routes/_authenticated'
import { catchErrors } from '~/utils/error-component'

export const Route = createFileRoute('/_authenticated/users/$userId')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    params: {
        parse: ({ userId }): { userId: number } => ({ userId: Number(userId) }),
    },
    component: RouteComponent,
    errorComponent: catchErrors([NotFoundError, NotFoundPage], [UnauthorizedError, NotFoundPage]),
    loader: async ({ params: { userId }, context: { client, queryClient } }) => {
        if (!Number.isNaN(userId)) {
            const u = await queryClient.ensureQueryData(userQueryOptions(client, userId))
            switch (u.type) {
                case UserType.STUDENT:
                    await queryClient.ensureQueryData(selectionsQueryOptions(client, userId))
                    break

                case UserType.TEACHER:
                    await queryClient.ensureQueryData(teacherSubjectsQueryOptions(client, userId))
                    break
            }
        }
    },
})

function RouteComponent() {
    const params = Route.useParams()

    const { client } = useAPI()
    const { string } = useI18n()

    const userQuery = createQuery(() => {
        const id = params().userId
        return {
            ...userQueryOptions(client, id),
            enabled: !Number.isNaN(id),
        }
    })

    const loadedUser = () => userQuery.data
    const title = () => loadedUser()?.displayName

    return (
        <Page name={title()} allowBacking>
            <Show when={loadedUser()}>
                {u => (
                    <UserInfoContextProvider value={{ user: u() }}>
                        <VStack gap={16}>
                            <div class="padded">
                                <UserProfile />
                            </div>
                            <VStack gap={0}>
                                <Switch>
                                    <Match when={u().isStudent()}>
                                        <h1 class="padded no-block-padding text-primary m3-title-medium">
                                            <IconLabel icon={TicketIcon} text={string.SELECTIONS()} />
                                        </h1>
                                        <SuspenseLoadingPage debugName="StudentInfoSelections">
                                            <StudentSelectionsTab userId={params().userId} />
                                        </SuspenseLoadingPage>
                                    </Match>
                                    <Match when={u().isTeacher()}>
                                        <p class="padded no-block-padding text-primary m3-title-medium">
                                            <IconLabel icon={BookIcon} text={string.SUBJECTS()} />
                                        </p>
                                        <SuspenseLoadingPage debugName="TeacherInfoSubjects">
                                            <TeacherSubjectsTab userId={params().userId} />
                                        </SuspenseLoadingPage>
                                    </Match>
                                </Switch>
                            </VStack>
                        </VStack>
                    </UserInfoContextProvider>
                )}
            </Show>
        </Page>
    )
}
