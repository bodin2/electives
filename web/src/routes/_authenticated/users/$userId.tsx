import TicketIcon from '@iconify-icons/mdi/ticket'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createRenderEffect, onCleanup, Show } from 'solid-js'
import { NotFoundError, UnauthorizedError } from '~/api'
import IconLabel from '~/components/IconLabel'
import Page from '~/components/Page'
import { SuspenseLoadingPage } from '~/components/pages/LoadingPage'
import NotFoundPage from '~/components/pages/NotFoundPage'
import { VStack } from '~/components/Stack'
import StudentSelectionsTab from '~/components/users/StudentSelectionsTab'
import { useUserDisplayContext } from '~/components/users/UserDisplayContext'
import { UserProfile } from '~/components/users/UserProfile'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { userQueryOptions } from '~/queries/users'
import { catchErrors } from '~/utils/error-component'

export const Route = createFileRoute('/_authenticated/users/$userId')({
    params: {
        parse: ({ userId }): { userId: number } => ({ userId: Number(userId) }),
    },
    component: RouteComponent,
    errorComponent: catchErrors([NotFoundError, NotFoundPage], [UnauthorizedError, NotFoundPage]),
    loader: async ({ params: { userId }, context: { client, queryClient } }) => {
        if (!Number.isNaN(userId)) {
            await queryClient.ensureQueryData(userQueryOptions(client, userId))
        }
    },
})

function RouteComponent() {
    const params = Route.useParams()

    const { client } = useAPI()
    const { string } = useI18n()
    const displayContext = useUserDisplayContext()

    const userQuery = createQuery(() => {
        const id = params().userId
        return {
            ...userQueryOptions(client, id),
            enabled: !Number.isNaN(id),
        }
    })

    const loadedUser = () => userQuery.data

    const title = () => loadedUser()?.displayName

    createRenderEffect(() => {
        displayContext.setUser(loadedUser())
    })

    onCleanup(() => {
        displayContext.setUser(undefined)
    })

    return (
        <Page name={title()} allowBacking leading={null} trailing={null}>
            <Show when={loadedUser()}>
                <VStack gap={16}>
                    <div class="padded">
                        <UserProfile />
                    </div>
                    <VStack gap={0}>
                        <h1 class="padded no-block-padding text-primary m3-title-medium">
                            <IconLabel icon={TicketIcon} text={string.SELECTIONS()} />
                        </h1>
                        <SuspenseLoadingPage debugName="StudentInfoSelections">
                            <StudentSelectionsTab userId={params().userId} />
                        </SuspenseLoadingPage>
                    </VStack>
                </VStack>
            </Show>
        </Page>
    )
}
