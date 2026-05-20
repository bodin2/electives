import CloseIcon from '@iconify-icons/mdi/close'
import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import SwapHorizontalIcon from '@iconify-icons/mdi/swap-horizontal'
import { createQuery, keepPreviousData, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { TextField } from 'm3-solid/src'
import { createEffect, createMemo, createSignal, For, Match, Show, Switch } from 'solid-js'
import { Portal } from 'solid-js/web'
import { ConflictError, GroupType, NotFoundError, type User } from '~/api'
import PaginatedUserList, { type PaginatedUserListHandle } from '~/components/admin/PaginatedUserList'
import { Button } from '~/components/Button'
import AddStudentToGroupDialog from '~/components/dialogs/AddStudentToGroupDialog'
import AddTeacherToGroupDialog from '~/components/dialogs/AddTeacherToGroupDialog'
import { ConfirmDialog } from '~/components/dialogs/base/ConfirmDialog'
import { SelectGroupDialog } from '~/components/dialogs/SelectGroupDialog'
import { GroupSelect } from '~/components/GroupSelect'
import Page from '~/components/Page'
import { SuspenseLoadingPage } from '~/components/pages/LoadingPage'
import NotFoundPage from '~/components/pages/NotFoundPage'
import { Option, Select } from '~/components/Select'
import { HStack, VStack } from '~/components/Stack'
import StickyTabs from '~/components/StickyTabs'
import { useTabPersistence } from '~/hooks/useTabPersistence'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import {
    groupManagersQueryOptions,
    groupMembersQueryOptions,
    groupQueryOptions,
    groupsQueryOptions,
} from '~/queries/groups'
import { debounce, nonNull } from '~/utils'
import { catchErrors } from '~/utils/error-component'
import { simpleXXHash31 } from '~/utils/xxhash'

export const Route = createFileRoute('/_adminAuthenticated/manage/groups/$groupId')({
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    validateSearch: (search: Record<string, unknown>): { page: number; tab?: 'info' | 'members' | 'managers' } => ({
        page: Math.max(Number(search?.page ?? 1), 1),
        tab: search?.tab as 'info' | 'members' | 'managers' | undefined,
    }),
    loaderDeps: ({ search }) => ({ page: search.page }),
    loader: async ({ params: { groupId }, context: { client, queryClient }, deps: { page } }) => {
        if (isNewRoute(groupId)) return

        const groupIdNum = Number(groupId)
        await Promise.all([
            queryClient.ensureQueryData(groupsQueryOptions(client)),
            queryClient.ensureQueryData(groupQueryOptions(client, groupIdNum)),
            queryClient.prefetchQuery(groupMembersQueryOptions(client, groupIdNum, page)),
            queryClient.prefetchQuery(groupManagersQueryOptions(client, groupIdNum, page)),
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

    const groupQuery = createQuery(() => ({
        ...groupQueryOptions(client, Number(params().groupId)),
        enabled: !isNew(),
    }))

    const [tab, setTab] = createSignal<'info' | 'members' | 'managers'>('info')
    useTabPersistence(tab, setTab)

    const [name, setName] = createSignal('')
    const [type, setType] = createSignal<GroupType>(GroupType.CUSTOM)
    const [parentId, setParentId] = createSignal<number | null>(null)

    // Reset local signals when group data changes
    createEffect(() => {
        const t = groupQuery.data
        if (t) {
            setName(t.name)
            setType(t.type)
            setParentId(t.parentId ?? null)
        } else if (isNew()) {
            setName('')
            setType(GroupType.CUSTOM)
            setParentId(null)
        }
    })

    const groupsQuery = createQuery(() => ({
        ...groupsQueryOptions(client),
        notifyOnChangeProps: ['data'],
    }))

    const parentCandidates = createMemo(() => {
        const all = groupsQuery.data ?? []
        return all.filter(g => g.id !== Number(params().groupId) && g.isRoot() && all.some(gg => gg.parentId !== g.id))
    })

    const isNonParentGroup = () => {
        if (isNew()) return true

        return groupsQuery.data?.every(g => g.parentId !== nonNull(groupQuery.data).id) ?? true
    }

    const handleSave = async () => {
        const trimmed = name().trim()
        if (!trimmed) return

        while (true) {
            try {
                if (isNew()) {
                    const id = simpleXXHash31(`${trimmed}:${performance.now()}`, Math.floor(Math.random() * 0x7fffffff))
                    await client.groups.admin.put(id, {
                        id,
                        name: trimmed,
                        type: type(),
                        parentId: parentId() ?? undefined,
                    })

                    qc.removeQueries({ queryKey: ['groups'], exact: true })
                    qc.removeQueries({ queryKey: ['group', id] })

                    // After creating, we should probably navigate to the new ID
                    navigate({ params: { groupId: id.toString() }, search: { page: 1 }, replace: true })
                } else {
                    await client.groups.admin.patch(Number(params().groupId), {
                        name: trimmed,
                        parentId: parentId() ?? undefined,
                        patchParentId: true,
                    })
                }

                break
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))

                if (e instanceof ConflictError) continue

                break
            }
        }
    }

    return (
        <Page name={isNew() ? string.CREATE_GROUP() : name()} allowBacking leading={null} trailing={null}>
            <Show when={!isNew()}>
                <StickyTabs
                    value={tab()}
                    onChange={setTab}
                    tabs={[
                        { label: string.GROUP(), value: 'info' },
                        { label: string.MEMBERS_LIST(), value: 'members' },
                        { label: string.MANAGER_TEACHERS(), value: 'managers' },
                    ]}
                />
            </Show>

            <Switch>
                <Match when={tab() === 'info' || isNew()}>
                    <VStack gap={16} style={{ padding: '16px' }}>
                        <TextField label={string.NAME()} value={name()} onInput={e => setName(e.currentTarget.value)} />
                        <Show
                            when={isNew()}
                            fallback={
                                <Show when={groupQuery.data}>
                                    {g => (
                                        <TextField
                                            readOnly
                                            label={string.GROUP_TYPE()}
                                            // @ts-expect-error: Dynamic keys
                                            value={string[`GROUP_TYPE_${GroupType[g().type]}`]()}
                                            supportingText={string.GROUP_TYPE_CANNOT_CHANGE_HINT()}
                                        />
                                    )}
                                </Show>
                            }
                        >
                            <Select
                                label={string.GROUP_TYPE()}
                                value={String(type())}
                                onChange={e => setType(Number(e.currentTarget.value) as GroupType)}
                            >
                                <For
                                    each={(
                                        [GroupType.CUSTOM, GroupType.GRADE, GroupType.ROOM, GroupType.PROGRAM] as const
                                    ).map(t => [GroupType[t] as 'CUSTOM' | 'GRADE' | 'ROOM' | 'PROGRAM', t] as const)}
                                >
                                    {([key, t]) => (
                                        <Option value={t} selected={type() === t}>
                                            {string[`GROUP_TYPE_${key}`]()}
                                        </Option>
                                    )}
                                </For>
                            </Select>
                        </Show>
                        <GroupSelect
                            disabled={!isNonParentGroup()}
                            supportingText={isNonParentGroup() ? undefined : string.CANNOT_SET_PARENT_GROUP_PARENT()}
                            label={string.PARENT_GROUP()}
                            placeholder={string.NO_PARENT_GROUP()}
                            value={parentId()}
                            groups={parentCandidates()}
                            onInput={setParentId}
                        />
                        <Button variant="filled" onClick={handleSave} disabled={!name().trim()}>
                            {string.SAVE()}
                        </Button>
                    </VStack>
                </Match>
                <Match when={tab() === 'members' && !isNew()}>
                    <SuspenseLoadingPage debugName="GroupMembers">
                        <GroupMembers />
                    </SuspenseLoadingPage>
                </Match>
                <Match when={tab() === 'managers' && !isNew()}>
                    <SuspenseLoadingPage debugName="GroupManagers">
                        <GroupManagers />
                    </SuspenseLoadingPage>
                </Match>
            </Switch>
        </Page>
    )
}

function GroupMembers() {
    const search = Route.useSearch()
    const navigate = Route.useNavigate()
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()

    const groupId = () => Number(params().groupId)

    const [query, setQuery] = createSignal<string | undefined>(undefined)
    const [addDialogOpen, setAddDialogOpen] = createSignal(false)
    const [deleteDialogOpen, setDeleteDialogOpen] = createSignal(false)
    const [migrateDialogOpen, setMigrateDialogOpen] = createSignal(false)
    let listHandle: PaginatedUserListHandle | undefined

    const groupQuery = createQuery(() => ({
        ...groupQueryOptions(client, groupId()),
        notifyOnChangeProps: ['data'],
    }))

    const allGroupsQuery = createQuery(() => ({
        ...groupsQueryOptions(client),
        // Only needed once the user opens the migrate dialog.
        enabled: migrateDialogOpen(),
        notifyOnChangeProps: ['data'],
    }))

    // Same-type groups, excluding the current one.
    const migrateCandidates = createMemo(() => {
        if (!allGroupsQuery.isSuccess || !groupQuery.isSuccess) return []
        const all = allGroupsQuery.data
        const currentType = groupQuery.data.type
        if (currentType === undefined) return []
        return all.filter(g => g.type === currentType && g.id !== groupId())
    })

    // @ts-expect-error: TypeScript moment
    const isFixedGroup = () => [GroupType.PROGRAM, GroupType.CUSTOM].includes(groupQuery.data?.type)

    const membersQuery = createQuery(() => ({
        ...groupMembersQueryOptions(client, groupId(), search().page, query()),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data'],
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    const hasNoMembers = () => membersQuery.isSuccess && membersQuery.data?.users.length === 0

    const handleDeleteMembers = async () => {
        try {
            await client.groups.admin.deleteMembers(groupId())
            await Promise.all([
                qc.invalidateQueries({ queryKey: ['groups', groupId(), 'members'] }),
                qc.invalidateQueries({ queryKey: ['groups', 'memberCounts'] }),
            ])
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_FAILED({ error: String(e) }))
        }
    }

    const handleMigrate = async (targetGroupId: number | null) => {
        if (targetGroupId === null) return
        try {
            await client.groups.admin.migrateMembers(groupId(), targetGroupId)
            await Promise.all([
                qc.invalidateQueries({ queryKey: ['groups', groupId(), 'members'] }),
                qc.invalidateQueries({ queryKey: ['groups', targetGroupId, 'members'] }),
                qc.invalidateQueries({ queryKey: ['groups', 'memberCounts'] }),
            ])
        } catch (e) {
            console.error(e)
            alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
        }
    }

    const removeUserFromGroup = async (user: User) => {
        try {
            if (!isFixedGroup()) {
                // Fixed slotted (GRADE/ROOM) memberships can't be removed directly
                // The student must be reassigned to a different group of the same type via the user details page
                // The remove button is hidden for those groups (see below), so this is just a defensive guard
                throw new Error(
                    'Cannot remove a member from a non-CUSTOM group; migrate all members to a different group of the same type first.',
                )
            }

            await client.users.admin.patch(user.id, {
                patchLastName: false,
                patchAvatarUrl: false,
                patchMiddleName: false,
                patchPrefix: false,
                patchProgramId: false,
                patchGroups: true,
                // `groups` on UserPatch is the replacement list of CUSTOM-typed memberships.
                groups: user.customGroups.filter(g => g.id !== groupId()).map(g => g.id),
            })
            listHandle?.onUserRemove(user.id)
        } catch (e) {
            console.error(e)
            alert(`Failed to remove user from group: ${e}`)
        }
    }

    return (
        <div style={{ '--sticky-offset': '48px' }}>
            <Portal>
                <AddStudentToGroupDialog
                    open={addDialogOpen()}
                    onClose={() => setAddDialogOpen(false)}
                    onSuccess={u => listHandle?.onUserAdd(u)}
                    groupId={groupId()}
                    groupType={groupQuery.data?.type ?? GroupType.CUSTOM}
                />
                <ConfirmDialog
                    open={deleteDialogOpen()}
                    variant="danger"
                    closedBy="any"
                    onCancel={() => setDeleteDialogOpen(false)}
                    onConfirm={async () => {
                        await handleDeleteMembers()
                        setDeleteDialogOpen(false)
                    }}
                    confirmText={string.DELETE_MEMBERS()}
                    headline={string.DELETE_MEMBERS()}
                >
                    <p>{string.CONFIRM_DELETE_MEMBERS({ name: <strong>{groupQuery.data?.name ?? ''}</strong> })}</p>
                </ConfirmDialog>
                <SelectGroupDialog
                    open={migrateDialogOpen()}
                    onClose={() => setMigrateDialogOpen(false)}
                    onSave={handleMigrate}
                    groups={migrateCandidates()}
                    value={null}
                    headline={string.MIGRATE_MEMBERS()}
                    description={string.MIGRATE_MEMBERS_HINT()}
                    showReset={false}
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
                onPagePreload={page => qc.prefetchQuery(groupMembersQueryOptions(client, groupId(), page))}
                onRefresh={() => qc.invalidateQueries({ queryKey: ['groups', groupId(), 'members'] })}
                headerRight={() => (
                    <HStack gap={8} alignVertical="center">
                        <Button
                            disabled={hasNoMembers()}
                            onClick={() => setDeleteDialogOpen(true)}
                            size="xs"
                            variant="tonal-error"
                            icon={DeleteIcon}
                        >
                            {string.DELETE_MEMBERS()}
                        </Button>
                        <Button
                            disabled={hasNoMembers()}
                            onClick={() => setMigrateDialogOpen(true)}
                            size="xs"
                            variant="tonal"
                            icon={SwapHorizontalIcon}
                        >
                            {string.MIGRATE_MEMBERS()}
                        </Button>
                        <Button onClick={() => setAddDialogOpen(true)} size="xs" icon={PlusIcon}>
                            {string.ADD_STUDENT()}
                        </Button>
                    </HStack>
                )}
                trailing={props => (
                    <Button
                        disabled={!isFixedGroup()}
                        aria-label={string.REMOVE()}
                        size="xs"
                        variant="tonal-error"
                        onClick={e => {
                            e.stopPropagation()
                            return removeUserFromGroup(props.user)
                        }}
                        icon={CloseIcon}
                        iconType="only"
                    />
                )}
            />
        </div>
    )
}

function GroupManagers() {
    const search = Route.useSearch()
    const navigate = Route.useNavigate()
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()

    const groupId = () => Number(params().groupId)

    const [query, setQuery] = createSignal<string | undefined>(undefined)
    const [addDialogOpen, setAddDialogOpen] = createSignal(false)
    let listHandle: PaginatedUserListHandle | undefined

    const managersQuery = createQuery(() => ({
        ...groupManagersQueryOptions(client, groupId(), search().page, query()),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data'],
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    const removeUserFromGroup = async (user: User) => {
        try {
            await client.users.admin.patch(user.id, {
                patchLastName: false,
                patchAvatarUrl: false,
                patchMiddleName: false,
                patchPrefix: false,
                patchProgramId: false,
                patchGroups: true,
                // Managers (teachers) can be in any group type, so we just filter the group out of their list.
                groups: user.groups.filter(g => g.id !== groupId()).map(g => g.id),
            })
            listHandle?.onUserRemove(user.id)
        } catch (e) {
            console.error(e)
            alert(`Failed to remove teacher from group: ${e}`)
        }
    }

    return (
        <div style={{ '--sticky-offset': '48px' }}>
            <Portal>
                <AddTeacherToGroupDialog
                    open={addDialogOpen()}
                    onClose={() => setAddDialogOpen(false)}
                    onSuccess={u => listHandle?.onUserAdd(u)}
                    groupId={groupId()}
                />
            </Portal>
            <PaginatedUserList
                searchLabel={string.SEARCH_TEACHERS()}
                onSearch={debouncedSetQuery()}
                ref={h => (listHandle = h)}
                page={search().page}
                data={managersQuery.data}
                onClick={user => navigate({ to: '/manage/users/$userId', params: { userId: String(user.id) } })}
                onPageChange={page => navigate({ search: { ...search(), page } })}
                onPagePreload={page => qc.prefetchQuery(groupManagersQueryOptions(client, groupId(), page))}
                onRefresh={() => qc.invalidateQueries({ queryKey: ['groups', groupId(), 'managers'] })}
                headerRight={() => (
                    <Button onClick={() => setAddDialogOpen(true)} size="xs" icon={PlusIcon}>
                        {string.ADD_TEACHER()}
                    </Button>
                )}
                trailing={props => (
                    <Button
                        aria-label={string.REMOVE()}
                        size="xs"
                        variant="tonal-error"
                        onClick={e => {
                            e.stopPropagation()
                            return removeUserFromGroup(props.user)
                        }}
                        icon={CloseIcon}
                        iconType="only"
                    />
                )}
            />
        </div>
    )
}
