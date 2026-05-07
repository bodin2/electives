import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import TeamList from '../../../../components/admin/TeamList'
import Page from '../../../../components/Page'
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
                memberCounts={memberCountsQuery.data ?? {}}
                onCreate={handleCreate}
                onEdit={handleEdit}
                onDelete={handleDelete}
            />
        </Page>
    )
}
