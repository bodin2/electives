import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import GroupList from '../../../../components/admin/GroupList'
import { Button } from '../../../../components/Button'
import Page from '../../../../components/Page'
import { VStack } from '../../../../components/Stack'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { groupMemberCountsQueryOptions, groupsQueryOptions } from '../../../../queries/groups'
import { nonNull } from '../../../../utils'
import type { Group } from '../../../../api/structures'

export const Route = createFileRoute('/_adminAuthenticated/manage/groups/')({
    component: RouteComponent,
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

    const handleCreate = () => {
        navigate({ to: '/manage/groups/$groupId', params: { groupId: 'new' }, search: { page: 0 } })
    }

    const handleEdit = (group: Group) => {
        navigate({
            to: '/manage/groups/$groupId',
            params: { groupId: group.id.toString() },
            search: { page: 0 },
        })
    }

    const handleDelete = async (group: Group) => {
        try {
            await client.groups.admin.delete(group.id)
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_FAILED({ error: String(e) }))
        }
    }

    return (
        <Page name={string.GROUPS()} leading={null} trailing={null}>
            <GroupList
                groups={nonNull(groupsQuery.data)}
                memberCounts={nonNull(memberCountsQuery.data)}
                onCreate={handleCreate}
                onEdit={handleEdit}
                onDelete={handleDelete}
                emptyElement={
                    <VStack grow alignHorizontal="center" alignVertical="center" gap={16}>
                        <VStack alignHorizontal="center">
                            <h1 class="m3-headline-medium text-balance">{string.NO_GROUPS_HINT()}</h1>
                            <p class="m3-body-large text-surface-variant text-center text-balance">
                                {string.NO_GROUPS_HINT_DESCRIPTION()}
                            </p>
                        </VStack>
                        <Button size="m" variant="filled" icon={PlusIcon} onClick={handleCreate}>
                            {string.CREATE_GROUP()}
                        </Button>
                    </VStack>
                }
            />
        </Page>
    )
}
