import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { NotFoundError, UnauthorizedError } from '~/api/types'
import GroupList from '~/components/admin/GroupList'
import Page from '~/components/Page'
import NotFoundPage from '~/components/pages/NotFoundPage'
import { VStack } from '~/components/Stack'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { groupMemberCountsQueryOptions, groupsQueryOptions } from '~/queries/groups'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '~/routes/_authenticated'
import { nonNull } from '~/utils'
import { catchErrors } from '~/utils/error-component'
import type { Group } from '~/api/structures'

export const Route = createFileRoute('/_authenticated/groups/')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    component: RouteComponent,
    errorComponent: catchErrors([NotFoundError, NotFoundPage], [UnauthorizedError, NotFoundPage]),
    loader: async ({ context: { client, queryClient } }) => {
        await Promise.all([
            queryClient.ensureQueryData(groupsQueryOptions(client)),
            queryClient.ensureQueryData(groupMemberCountsQueryOptions(client)),
        ])
    },
})

function RouteComponent() {
    const navigate = Route.useNavigate()
    const { client } = useAPI()
    const { string } = useI18n()

    const groupsQuery = createQuery(() => ({ ...groupsQueryOptions(client), notifyOnChangeProps: ['data'] }))
    const memberCountsQuery = createQuery(() => ({
        ...groupMemberCountsQueryOptions(client),
        notifyOnChangeProps: ['data'],
    }))

    const handleEdit = (group: Group) => {
        navigate({
            to: '/groups/$groupId',
            params: { groupId: group.id.toString() },
            search: { page: 0 },
        })
    }

    return (
        <Page name={string.GROUPS()} leading={null} trailing={null}>
            <GroupList
                noEditIcon
                groups={nonNull(groupsQuery.data)}
                memberCounts={nonNull(memberCountsQuery.data)}
                onClick={handleEdit}
                emptyElement={
                    <VStack grow alignHorizontal="center" alignVertical="center" gap={16}>
                        <VStack alignHorizontal="center">
                            <h1 class="m3-headline-medium text-balance">{string.NO_GROUPS_HINT()}</h1>\
                        </VStack>
                    </VStack>
                }
            />
        </Page>
    )
}
