import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { TextField } from 'm3-solid'
import { createSignal } from 'solid-js'
import { Button } from '../../../../components/Button'
import Page from '../../../../components/Page'
import { VStack } from '../../../../components/Stack'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'

export const Route = createFileRoute('/_adminAuthenticated/manage/teams/$teamId')({
    component: RouteComponent,
    loader: async ({ params: { teamId }, context: { client } }) => {
        if (teamId === 'new') return null
        const team = await client.teams.fetch(Number(teamId))
        return team
    },
})

const isNewRoute = (teamId: string) => teamId === 'new'

const tryCoerceValidId = (id: string) => {
    const parsed = Number(id)
    return Number.isNaN(parsed) ? null : parsed < 0 ? null : parsed
}

function RouteComponent() {
    const { teamId } = Route.useParams()()
    const { client } = useAPI()
    const { string } = useI18n()
    const router = useRouter()

    const isNew = isNewRoute(teamId)
    const data = Route.useLoaderData()
    const [name, setName] = createSignal(data()?.name ?? '')
    const [id, setId] = createSignal(data()?.id.toString() ?? '')

    const handleSave = async () => {
        const idParsed = tryCoerceValidId(id())
        const trimmed = name().trim()
        if (!trimmed || !idParsed) return

        try {
            if (isNew) {
                await client.teams.admin.put(idParsed, { id: idParsed, name: trimmed })
            } else {
                await client.teams.admin.patch(Number(teamId), { name: trimmed })
            }

            await client.teams.fetchAll({ force: true })
            await router.invalidate()
        } catch (e) {
            console.error(e)
            alert(`Failed to save team: ${e}`)
        } finally {
            history.back()
        }
    }

    return (
        <Page name={isNew ? string.CREATE_TEAM() : string.EDIT_TEAM()} leading={null} trailing={null} allowBacking>
            <VStack gap={16} style={{ padding: '16px' }}>
                <TextField
                    label={string.ID()}
                    value={id()}
                    onInput={e => setId(e.currentTarget.value)}
                    disabled={!isNew}
                    error={tryCoerceValidId(id()) === null}
                />
                <TextField label={string.NAME()} value={name()} onInput={e => setName(e.currentTarget.value)} />
                <Button variant="filled" onClick={handleSave} disabled={!name().trim()}>
                    {string.SAVE()}
                </Button>
            </VStack>
        </Page>
    )
}
