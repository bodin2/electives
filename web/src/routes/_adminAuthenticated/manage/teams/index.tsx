import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import TeamList from '../../../../components/admin/TeamList'
import { Button } from '../../../../components/Button'
import Page from '../../../../components/Page'
import { VStack } from '../../../../components/Stack'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { teamMemberCountsQueryOptions, teamsQueryOptions } from '../../../../queries/teams'
import { nonNull } from '../../../../utils'
import type { Team } from '../../../../api/structures'

export const Route = createFileRoute('/_adminAuthenticated/manage/teams/')({
    component: RouteComponent,
    loader: async ({ context: { client, queryClient } }) => {
        await Promise.all([
            queryClient.ensureQueryData(teamsQueryOptions(client)),
            queryClient.ensureQueryData(teamMemberCountsQueryOptions(client)),
        ])
    },
})

function RouteComponent() {
    const navigate = Route.useNavigate()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()

    const teamsQuery = createQuery(() => ({ ...teamsQueryOptions(client), notifyOnChangeProps: ['data'] }))
    const memberCountsQuery = createQuery(() => ({
        ...teamMemberCountsQueryOptions(client),
        notifyOnChangeProps: ['data'],
    }))

    const invalidate = () => qc.invalidateQueries({ queryKey: ['teams'] })

    const handleCreate = () => {
        navigate({ to: '/manage/teams/$teamId', params: { teamId: 'new' }, search: { page: 0 } })
    }

    const handleEdit = (team: Team) => {
        navigate({
            to: '/manage/teams/$teamId',
            params: { teamId: team.id.toString() },
            search: { page: 0 },
        })
    }

    const handleDelete = async (team: Team) => {
        try {
            await client.teams.admin.delete(team.id)
            await invalidate()
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_FAILED({ error: String(e) }))
        }
    }

    return (
        <Page name={string.TEAMS()} leading={null} trailing={null}>
            <TeamList
                teams={nonNull(teamsQuery.data)}
                memberCounts={nonNull(memberCountsQuery.data)}
                onCreate={handleCreate}
                onEdit={handleEdit}
                onDelete={handleDelete}
                emptyElement={
                    <VStack grow alignHorizontal="center" alignVertical="center" gap={16}>
                        <VStack alignHorizontal="center">
                            <h1 class="m3-headline-medium text-balance">{string.NO_TEAMS_HINT()}</h1>
                            <p class="m3-body-large text-surface-variant text-center text-balance">
                                {string.NO_TEAMS_HINT_DESCRIPTION()}
                            </p>
                        </VStack>
                        <Button size="m" variant="filled" icon={PlusIcon} onClick={handleCreate}>
                            {string.CREATE_TEAM()}
                        </Button>
                    </VStack>
                }
            />
        </Page>
    )
}
