import CloseIcon from '@iconify-icons/mdi/close'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createFileRoute, defer, useRouter } from '@tanstack/solid-router'
import { LoadingIndicator, TextField } from 'm3-solid'
import { createEffect, createResource, createSignal, Match, on, onMount, Show, Suspense, Switch } from 'solid-js'
import { Portal } from 'solid-js/web'
import { NotFoundError, type User } from '../../../../api'
import PaginatedUserList, { type PaginatedUserListHandle } from '../../../../components/admin/PaginatedUserList'
import { Button } from '../../../../components/Button'
import AddStudentToTeamDialog from '../../../../components/dialogs/AddStudentToTeamDialog'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { VStack } from '../../../../components/Stack'
import StickyTabs from '../../../../components/StickyTabs'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { nonNull } from '../../../../utils'
import { catchErrors } from '../../../../utils/error-component'

export const Route = createFileRoute('/_adminAuthenticated/manage/teams/$teamId')({
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    validateSearch: (search: Record<string, unknown>): { page: number; tab?: 'info' | 'members' } => ({
        page: Math.max(Number(search?.page ?? 1), 1),
        tab: search?.tab as 'info' | 'members' | undefined,
    }),
    loaderDeps: ({ search }) => ({ page: search.page }),
    loader: async ({ params: { teamId }, context: { client }, deps: { page } }) => {
        if (isNewRoute(teamId)) return { team: null, initialMembers: null }
        const team = await client.teams.fetch(Number(teamId))
        const initialMembers = client.teams.admin.fetchMembers(Number(teamId), page)
        return { team, initialMembers: defer(initialMembers) }
    },
    component: RouteComponent,
})

const isNewRoute = (teamId: string) => teamId === 'new'

const tryCoerceValidId = (id: string) => {
    const parsed = Number(id)
    return Number.isNaN(parsed) ? null : parsed < 0 ? null : parsed
}

function RouteComponent() {
    const params = Route.useParams()
    const search = Route.useSearch()
    const { client } = useAPI()
    const router = useRouter()
    const { string } = useI18n()
    const navigate = Route.useNavigate()
    const data = Route.useLoaderData()
    const [initialMembers] = createResource(() => data().initialMembers)

    const isNew = () => isNewRoute(params().teamId)

    const [tab, setTab] = createSignal<'info' | 'members'>('info')
    const [name, setName] = createSignal('')
    const [id, setId] = createSignal('')

    // Reset local signals when team data changes
    createEffect(() => {
        const t = data().team
        if (t) {
            setName(t.name)
            setId(t.id.toString())
        } else {
            setName('')
            setId('')
        }
    })

    onMount(() => {
        const tab = search().tab
        if (tab) {
            setTab(tab)
        }
    })

    createEffect(
        on(tab, tab => {
            navigate({ search: { ...search(), tab }, replace: true })
        }),
    )

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
                await router.invalidate({ filter: r => r.id === Route.id || r.id === Route.parentRoute.id })
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
                <StickyTabs
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
                        <Show when={initialMembers()} fallback={<LoadingIndicator container />}>
                            <TeamMembers initialMembers={nonNull(initialMembers())} />
                        </Show>
                    </Match>
                </Switch>
            </Suspense>
        </Page>
    )
}

function TeamMembers(props: { initialMembers: { users: User[]; total: number } }) {
    const search = Route.useSearch()
    const navigate = Route.useNavigate()
    const params = Route.useParams()
    const router = useRouter()
    const { client } = useAPI()
    const { string } = useI18n()

    const [addDialogOpen, setAddDialogOpen] = createSignal(false)
    let listHandle: PaginatedUserListHandle | undefined

    const removeUserFromTeam = async (user: User) => {
        const teamId = Number(params().teamId)
        try {
            await client.users.admin.patch(user.id, {
                patchLastName: false,
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
        <div style={{ '--sticky-offset': '48px' }}>
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
                data={props.initialMembers}
                isLoading={router.state.status === 'pending'}
                onClick={user => navigate({ to: '/manage/users/$userId', params: { userId: String(user.id) } })}
                onPageChange={page => navigate({ search: { ...search(), page } })}
                onPagePreload={page =>
                    router.preloadRoute({ to: Route.fullPath, search: { ...search(), page }, params: params() })
                }
                onRefresh={() => router.invalidate({ filter: r => r.id === Route.id, sync: true })}
                headerRight={() => (
                    <Button onClick={() => setAddDialogOpen(true)} size="xs" icon={PlusIcon}>
                        {string.ADD_STUDENT()}
                    </Button>
                )}
                // listHeader={() => (
                //     <ListItem
                //         lines={2}
                //         headline={string.ADD_STUDENT()}
                //         leading={<ContainedIcon icon={PlusIcon} />}
                //         onClick={() => setAddDialogOpen(true)}
                //     />
                // )}
                trailing={props => (
                    <Button
                        aria-label={string.REMOVE()}
                        size="xs"
                        variant="tonal-error"
                        onClick={e => {
                            e.stopPropagation()
                            return removeUserFromTeam(props.user)
                        }}
                        icon={CloseIcon}
                        iconType="only"
                    />
                )}
            />
        </div>
    )
}
