import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { TextField } from 'm3-solid/src'
import { createMemo, createRenderEffect, createSignal, For, Match, Show, Switch } from 'solid-js'
import { ConflictError, GroupType, NotFoundError } from '~/api'
import { Button } from '~/components/Button'
import { GroupSelect } from '~/components/GroupSelect'
import { GroupDisplayContextProvider } from '~/components/groups/GroupDisplayContext'
import { GroupManagers } from '~/components/groups/GroupManagers'
import { GroupMembers } from '~/components/groups/GroupMembers'
import Page from '~/components/Page'
import { SuspenseLoadingPage } from '~/components/pages/LoadingPage'
import NotFoundPage from '~/components/pages/NotFoundPage'
import { Option, Select } from '~/components/Select'
import { VStack } from '~/components/Stack'
import StickyTabs from '~/components/StickyTabs'
import { useTabPersistence } from '~/hooks/useTabPersistence'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentsQueryOptions } from '~/queries/enrollments'
import {
    groupManagersQueryOptions,
    groupMembersQueryOptions,
    groupQueryOptions,
    groupsQueryOptions,
} from '~/queries/groups'
import { nonNull } from '~/utils'
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
            queryClient.ensureQueryData(enrollmentsQueryOptions(client)),
            queryClient.prefetchQuery(groupMembersQueryOptions(client, groupIdNum, page)),
            queryClient.prefetchQuery(groupManagersQueryOptions(client, groupIdNum, page)),
        ])
    },
    component: RouteComponent,
})

const isNewRoute = (groupId: string) => groupId === 'new'

function RouteComponent() {
    const params = Route.useParams()
    const search = Route.useSearch()
    const { client } = useAPI()
    const { string } = useI18n()
    const navigate = Route.useNavigate()
    const qc = useQueryClient()

    const isNew = () => isNewRoute(params().groupId)
    const groupId = () => Number(params().groupId)

    const groupQuery = createQuery(() => ({
        ...groupQueryOptions(client, groupId()),
        notifyOnChangeProps: ['data'],
        enabled: !isNew(),
    }))

    const [tab, setTab] = createSignal<'info' | 'members' | 'managers'>('info')
    useTabPersistence(tab, setTab)

    const [name, setName] = createSignal('')
    const [type, setType] = createSignal<GroupType>(GroupType.CUSTOM)
    const [parentId, setParentId] = createSignal<number | null>(null)

    // Reset local signals when group data changes
    createRenderEffect(() => {
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
        return all.filter(g => g.id !== groupId() && g.isRoot() && all.some(gg => gg.parentId !== g.id))
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
                    await client.groups.admin.patch(groupId(), {
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

    const onPageChange = (page: number) => navigate({ search: { ...search(), page } })

    return (
        <Page name={isNew() ? string.CREATE_GROUP() : name()} allowBacking>
            <Show when={!isNew()}>
                <StickyTabs
                    value={tab()}
                    onChange={setTab}
                    tabs={[
                        { label: string.GROUP(), value: 'info' },
                        { label: string.MEMBERS_LIST(), value: 'members' },
                        { label: string.MANAGERS(), value: 'managers' },
                    ]}
                />
            </Show>

            <GroupDisplayContextProvider value={{ editable: true }}>
                <Switch>
                    <Match when={tab() === 'info' || isNew()}>
                        <VStack gap={16} style={{ padding: '16px' }}>
                            <TextField
                                label={string.NAME()}
                                value={name()}
                                onInput={e => setName(e.currentTarget.value)}
                            />
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
                                            [
                                                GroupType.CUSTOM,
                                                GroupType.GRADE,
                                                GroupType.ROOM,
                                                GroupType.PROGRAM,
                                            ] as const
                                        ).map(
                                            t => [GroupType[t] as 'CUSTOM' | 'GRADE' | 'ROOM' | 'PROGRAM', t] as const,
                                        )}
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
                                supportingText={
                                    isNonParentGroup() ? undefined : string.CANNOT_SET_PARENT_GROUP_PARENT()
                                }
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
                            <GroupMembers groupId={groupId()} page={search().page} onPageChange={onPageChange} />
                        </SuspenseLoadingPage>
                    </Match>
                    <Match when={tab() === 'managers' && !isNew()}>
                        <SuspenseLoadingPage debugName="GroupManagers">
                            <GroupManagers groupId={groupId()} page={search().page} onPageChange={onPageChange} />
                        </SuspenseLoadingPage>
                    </Match>
                </Switch>
            </GroupDisplayContextProvider>
        </Page>
    )
}
