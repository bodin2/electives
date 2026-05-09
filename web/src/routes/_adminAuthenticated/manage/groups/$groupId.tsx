import CloseIcon from '@iconify-icons/mdi/close'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery, keepPreviousData, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { TextField } from 'm3-solid'
import { createEffect, createMemo, createSignal, Match, Show, Switch } from 'solid-js'
import { Portal } from 'solid-js/web'
import { ConflictError, NotFoundError, type User } from '../../../../api'
import PaginatedUserList, { type PaginatedUserListHandle } from '../../../../components/admin/PaginatedUserList'
import { Button } from '../../../../components/Button'
import AddStudentToTeamDialog from '../../../../components/dialogs/AddStudentToTeamDialog'
import { ConfirmDialog } from '../../../../components/dialogs/base/ConfirmDialog'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { VStack } from '../../../../components/Stack'
import StickyTabs from '../../../../components/StickyTabs'
import { useTabPersistence } from '../../../../hooks/useTabPersistence'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { teamMembersQueryOptions, teamQueryOptions } from '../../../../queries/teams'
import { debounce } from '../../../../utils'
import { catchErrors } from '../../../../utils/error-component'
import { simpleXXHash31 } from '../../../../utils/xxhash'

export const Route = createFileRoute('/_adminAuthenticated/manage/groups/$groupId')({
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    validateSearch: (search: Record<string, unknown>): { page: number; tab?: 'info' | 'members' } => ({
        page: Math.max(Number(search?.page ?? 1), 1),
        tab: search?.tab as 'info' | 'members' | undefined,
    }),
    loaderDeps: ({ search }) => ({ page: search.page }),
    loader: async ({ params: { groupId }, context: { client, queryClient }, deps: { page } }) => {
        if (isNewRoute(groupId)) return

        const groupIdNum = Number(groupId)
        await Promise.all([
            queryClient.ensureQueryData(teamQueryOptions(client, groupIdNum)),
            queryClient.prefetchQuery(teamMembersQueryOptions(client, groupIdNum, page)),
        ])
    },
    component: RouteComponent,
})

const isNewRoute = (groupId: string) => groupId === 'new'

function RouteComponent() {
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()
    const navigate = Route.useNavigate()
    const qc = useQueryClient()

    const isNew = () => isNewRoute(params().groupId)

    const teamQuery = createQuery(() => ({
        ...teamQueryOptions(client, Number(params().groupId)),
        enabled: !isNew(),
    }))

    const [confirmDeleteOpen, setConfirmDeleteOpen] = createSignal(false)
    const [tab, setTab] = createSignal<'info' | 'members'>('info')
    useTabPersistence(tab, setTab)

    const [name, setName] = createSignal('')

    // Reset local signals when team data changes
    createEffect(() => {
        const t = teamQuery.data
        if (t) {
            setName(t.name)
        } else if (isNew()) {
            setName('')
        }
    })

    const handleSave = async () => {
        const trimmed = name().trim()
        if (!trimmed) return

        while (true) {
            try {
                if (isNew()) {
                    const id = simpleXXHash31(`${trimmed}:${performance.now()}`, Math.floor(Math.random() * 0x7fffffff))
                    await client.teams.admin.put(id, { id, name: trimmed })
                    // After creating, we should probably navigate to the new ID
                    navigate({ params: { groupId: id.toString() }, search: { page: 1 }, replace: true })
                } else {
                    await client.teams.admin.patch(Number(params().groupId), { name: trimmed })
                }

                await client.teams.fetchAll({ force: true })
                await qc.invalidateQueries({ queryKey: ['teams'] })

                break
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))

                if (e instanceof ConflictError) continue

                break
            }
        }
    }

    const handleDelete = () => {
        if (!teamQuery.data) return
        setConfirmDeleteOpen(true)
    }

    const doDelete = async () => {
        const team = teamQuery.data
        if (!team) return

        try {
            await client.teams.admin.delete(team.id)
            await client.teams.fetchAll({ force: true })
            await qc.removeQueries({ queryKey: ['teams', team.id] })
            await qc.invalidateQueries({ queryKey: ['teams'] })
            navigate({ to: '..', replace: true })
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_FAILED({ error: String(e) }))
        } finally {
            setConfirmDeleteOpen(false)
        }
    }

    return (
        <Page name={isNew() ? string.CREATE_TEAM() : name()} allowBacking leading={null} trailing={null}>
            <Portal>
                <ConfirmDialog
                    open={confirmDeleteOpen()}
                    variant="danger"
                    closedBy="any"
                    onCancel={() => setConfirmDeleteOpen(false)}
                    onConfirm={doDelete}
                    confirmText={string.DELETE_TEAM()}
                    headline={string.DELETE_TEAM()}
                >
                    <p>{string.CONFIRM_DELETE_TEAM({ name: <strong>{teamQuery.data?.name ?? ''}</strong> })}</p>
                </ConfirmDialog>
            </Portal>

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

            <Switch>
                <Match when={tab() === 'info' || isNew()}>
                    <VStack gap={16} style={{ padding: '16px' }}>
                        <TextField label={string.NAME()} value={name()} onInput={e => setName(e.currentTarget.value)} />
                        <Button variant="filled" onClick={handleSave} disabled={!name().trim()}>
                            {string.SAVE()}
                        </Button>
                        <Show when={!isNew()}>
                            <Button variant="tonal-error" onClick={handleDelete}>
                                {string.DELETE_TEAM()}
                            </Button>
                        </Show>
                    </VStack>
                </Match>
                <Match when={tab() === 'members' && !isNew()}>
                    <TeamMembers />
                </Match>
            </Switch>
        </Page>
    )
}

function TeamMembers() {
    const search = Route.useSearch()
    const navigate = Route.useNavigate()
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()

    const teamId = () => Number(params().groupId)

    const [query, setQuery] = createSignal<string | undefined>(undefined)
    const [addDialogOpen, setAddDialogOpen] = createSignal(false)
    let listHandle: PaginatedUserListHandle | undefined

    const membersQuery = createQuery(() => ({
        ...teamMembersQueryOptions(client, teamId(), search().page, query()),
        placeholderData: keepPreviousData,
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    const removeUserFromTeam = async (user: User) => {
        try {
            await client.users.admin.patch(user.id, {
                patchLastName: false,
                patchAvatarUrl: false,
                patchMiddleName: false,
                patchPrefix: false,
                patchTeams: true,
                teams: user.teams.filter(t => t.id !== teamId()).map(t => t.id),
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
                    teamId={teamId()}
                />
            </Portal>
            <PaginatedUserList
                searchLabel={string.SEARCH_STUDENTS()}
                onSearch={debouncedSetQuery()}
                ref={h => (listHandle = h)}
                page={search().page}
                data={membersQuery.data}
                onClick={user => navigate({ to: '/manage/users/$userId', params: { userId: String(user.id) } })}
                onPageChange={page => navigate({ search: { ...search(), page } })}
                onPagePreload={page => qc.prefetchQuery(teamMembersQueryOptions(client, teamId(), page))}
                onRefresh={() => qc.invalidateQueries({ queryKey: ['teams', teamId(), 'members'] })}
                headerRight={() => (
                    <Button onClick={() => setAddDialogOpen(true)} size="xs" icon={PlusIcon}>
                        {string.ADD_STUDENT()}
                    </Button>
                )}
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
