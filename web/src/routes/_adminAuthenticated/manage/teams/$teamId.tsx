import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { ListItem, LoadingIndicator, Tabs, TextField } from 'm3-solid'
import { createEffect, createMemo, createResource, createSignal, Match, Show, Suspense, Switch } from 'solid-js'
import { Portal } from 'solid-js/web'
import { NotFoundError, type User } from '../../../../api'
import PaginatedUserList, { type PaginatedUserListHandle } from '../../../../components/admin/PaginatedUserList'
import { Button } from '../../../../components/Button'
import { ContainedIcon } from '../../../../components/ContainedIcon'
import AddStudentToTeamDialog from '../../../../components/dialogs/AddStudentToTeamDialog'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { VStack } from '../../../../components/Stack'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { catchErrors } from '../../../../utils/error-component'

type TeamSearch = {
    page: number
}

export const Route = createFileRoute('/_adminAuthenticated/manage/teams/$teamId')({
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    validateSearch: (search: Record<string, unknown>): TeamSearch => {
        return {
            page: Number(search?.page ?? 1) || 1,
        }
    },
    loader: async ({ params: { teamId }, context: { client } }) => {
        if (teamId === 'new') return null
        return await client.teams.fetch(Number(teamId))
    },
    component: RouteComponent,
})

const tryCoerceValidId = (id: string) => {
    const parsed = Number(id)
    return Number.isNaN(parsed) ? null : parsed < 0 ? null : parsed
}

function RouteComponent() {
    const params = Route.useParams()
    const { client } = useAPI()
    const router = useRouter()
    const { string } = useI18n()
    const navigate = Route.useNavigate()
    const search = Route.useSearch()
    const team = Route.useLoaderData()

    const isNew = () => params().teamId === 'new'

    const [tab, setTab] = createSignal<'info' | 'members'>('info')
    const [name, setName] = createSignal('')
    const [id, setId] = createSignal('')

    // Reset local signals when team data changes
    createEffect(() => {
        const t = team()
        if (t) {
            setName(t.name)
            setId(t.id.toString())
        } else {
            setName('')
            setId('')
        }
    })

    // Make sure cache key remains stable
    const membersSource = createMemo<[number, number] | undefined>(
        prev => {
            const s = search()
            const p = params()

            if (tab() === 'members' && p.teamId !== 'new') {
                return [Number(p.teamId), s.page]
            }

            return prev
        },
        undefined,
        { equals: (a, b) => a?.every((val, i) => val === b?.[i]) ?? a === b },
    )

    // So we only refetch when manually invalidated or unmounting
    const [members, { refetch: refetchMembers }] = createResource(membersSource, async source => {
        const [id, page] = source
        return client.teams.admin.fetchMembers(id, page)
    })

    const handleSave = async () => {
        const idParsed = tryCoerceValidId(id())
        const trimmed = name().trim()
        if (!trimmed || !idParsed) return

        try {
            if (isNew()) {
                await client.teams.admin.put(idParsed, { id: idParsed, name: trimmed })
                // After creating, we should probably navigate to the new ID
                navigate({ params: { teamId: idParsed.toString() }, search: { page: 1 } })
            } else {
                await client.teams.admin.patch(Number(params().teamId), { name: trimmed })
                await router.invalidate()
            }
            await client.teams.fetchAll({ force: true })
        } catch (e) {
            console.error(e)
            alert(`Failed to save team: ${e}`)
        }
    }

    return (
        <Page name={isNew() ? string.CREATE_TEAM() : string.EDIT_TEAM()} leading={null} trailing={null} allowBacking>
            <Show when={!isNew()}>
                <Tabs
                    value={tab()}
                    onChange={setTab}
                    tabs={[
                        { label: string.TEAM(), value: 'info' },
                        { label: string.MEMBERS_LIST(), value: 'members' },
                    ]}
                />
            </Show>

            <Suspense fallback={<LoadingIndicator container />}>
                <Switch>
                    <Match when={tab() === 'info' || isNew()}>
                        <VStack gap={16} style={{ padding: '16px' }}>
                            <TextField
                                label={string.ID()}
                                value={id()}
                                onInput={e => setId(e.currentTarget.value)}
                                disabled={!isNew()}
                                error={tryCoerceValidId(id()) === null}
                            />
                            <TextField
                                label={string.NAME()}
                                value={name()}
                                onInput={e => setName(e.currentTarget.value)}
                            />
                            <Button variant="filled" onClick={handleSave} disabled={!name().trim()}>
                                {string.SAVE()}
                            </Button>
                        </VStack>
                    </Match>
                    <Match when={tab() === 'members' && !isNew()}>
                        <Show when={members.latest} fallback={<LoadingIndicator container />}>
                            {members => <TeamMembers members={members()} refetchMembers={refetchMembers} />}
                        </Show>
                    </Match>
                </Switch>
            </Suspense>
        </Page>
    )
}

function TeamMembers(props: { members: { users: User[]; total: number }; refetchMembers: () => void }) {
    const search = Route.useSearch()
    const navigate = Route.useNavigate()
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()

    const [addDialogOpen, setAddDialogOpen] = createSignal(false)
    let listHandle: PaginatedUserListHandle | undefined

    const removeUserFromTeam = async (user: User) => {
        const teamId = Number(params().teamId)
        try {
            await client.users.admin.patch(user.id, {
                patchAvatarUrl: false,
                patchMiddleName: false,
                patchTeams: true,
                teams: user.teams.filter(t => t.id !== teamId).map(t => t.id),
            })
            listHandle?.onUserRemove(user.id)
        } catch (e) {
            console.error(e)
            alert(`Failed to remove user from team: ${e}`)
        }
    }

    return (
        <>
            <Portal>
                <AddStudentToTeamDialog
                    open={addDialogOpen()}
                    onClose={() => setAddDialogOpen(false)}
                    onSuccess={u => listHandle?.onUserAdd(u)}
                    teamId={Number(params().teamId)}
                />
            </Portal>
            <PaginatedUserList
                ref={h => (listHandle = h)}
                page={search().page}
                data={props.members}
                onPageChange={page => navigate({ search: { ...search(), page } })}
                onRefresh={() => props.refetchMembers()}
                listHeader={() => (
                    <ListItem
                        lines={2}
                        headline={string.ADD_STUDENT()}
                        leading={<ContainedIcon icon={PlusIcon} />}
                        onClick={() => setAddDialogOpen(true)}
                    />
                )}
                trailing={user => (
                    <Button
                        aria-label={string.REMOVE()}
                        variant="text"
                        onClick={e => {
                            e.stopPropagation()
                            removeUserFromTeam(user)
                        }}
                        icon={DeleteIcon}
                        iconType="only"
                    />
                )}
            />
        </>
    )
}
