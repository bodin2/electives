import { createFileRoute, useRouter } from '@tanstack/solid-router'
import TeamList from '../../../../components/admin/TeamList'
import Page from '../../../../components/Page'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import type { Team } from '../../../../api/structures'

export const Route = createFileRoute('/_adminAuthenticated/manage/teams/')({
    component: RouteComponent,
    loader: async ({ context: { client } }) => {
        const teams = await client.teams.fetchAll()
        const memberCounts = await client.teams.admin.fetchMemberCounts()
        return { teams, memberCounts }
    },
})

function RouteComponent() {
    const navigate = Route.useNavigate()
    const { client } = useAPI()
    const { string } = useI18n()
    const data = Route.useLoaderData()
    const router = useRouter()

    const invalidate = () => router.invalidate({ filter: r => r.routeId === Route.id, sync: true })

    const handleCreate = () => {
        navigate({ to: '/manage/teams/$teamId', params: { teamId: 'new' }, search: { page: 0 } })
        invalidate()
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
            alert('Failed to delete team')
        }
    }

    return (
        <Page name={string.TEAMS()} leading={null} trailing={null}>
            <TeamList
                teams={data().teams}
                memberCounts={data().memberCounts}
                onCreate={handleCreate}
                onEdit={handleEdit}
                onDelete={handleDelete}
            />
        </Page>
    )
}
