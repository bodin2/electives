import { createQuery, keepPreviousData, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createMemo, createSignal, Match, Switch } from 'solid-js'
import { NotFoundError, UnauthorizedError } from '~/api/types'
import PaginatedUserList from '~/components/admin/PaginatedUserList'
import Page from '~/components/Page'
import { SuspenseLoadingPage } from '~/components/pages/LoadingPage'
import NotFoundPage from '~/components/pages/NotFoundPage'
import StickyTabs from '~/components/StickyTabs'
import { useTabPersistence } from '~/hooks/useTabPersistence'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { groupManagersQueryOptions, groupMembersQueryOptions, groupQueryOptions } from '~/queries/groups'
import { debounce, nonNull } from '~/utils'
import { catchErrors } from '~/utils/error-component'

export const Route = createFileRoute('/_authenticated/groups/$groupId')({
    errorComponent: catchErrors([NotFoundError, NotFoundPage], [UnauthorizedError, NotFoundPage]),
    validateSearch: (search: Record<string, unknown>): { page: number; tab?: 'info' | 'members' | 'managers' } => ({
        page: Math.max(Number(search?.page ?? 1), 1),
        tab: search?.tab as 'info' | 'members' | 'managers' | undefined,
    }),
    loaderDeps: ({ search }) => ({ page: search.page }),
    loader: async ({ params: { groupId }, context: { client, queryClient }, deps: { page } }) => {
        if (isNewRoute(groupId)) return

        const groupIdNum = Number(groupId)
        await Promise.all([
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

    const groupQuery = createQuery(() => ({
        ...groupQueryOptions(client, Number(params().groupId)),
    }))

    const [tab, setTab] = createSignal<'members' | 'managers'>('members')
    useTabPersistence(tab, setTab)

    return (
        <Page name={nonNull(groupQuery.data).name} allowBacking leading={null} trailing={null}>
            <StickyTabs
                value={tab()}
                onChange={setTab}
                tabs={[
                    { label: string.MEMBERS_LIST(), value: 'members' },
                    { label: string.MANAGERS(), value: 'managers' },
                ]}
            />

            <Switch>
                <Match when={tab() === 'members'}>
                    <SuspenseLoadingPage debugName="ReadOnlyGroupMembers">
                        <GroupMembers />
                    </SuspenseLoadingPage>
                </Match>
                <Match when={tab() === 'managers'}>
                    <SuspenseLoadingPage debugName="ReadOnlyGroupManagers">
                        <GroupManagers />
                    </SuspenseLoadingPage>
                </Match>
            </Switch>
        </Page>
    )
}

// Note: Components are maintained separately because it's not worth the effort to merge both tab screens
// There's simply too many states in the writable version.

function GroupMembers() {
    const search = Route.useSearch()
    const navigate = Route.useNavigate()
    const params = Route.useParams()
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()

    const groupId = () => Number(params().groupId)

    const [query, setQuery] = createSignal<string | undefined>(undefined)

    const membersQuery = createQuery(() => ({
        ...groupMembersQueryOptions(client, groupId(), search().page, query()),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data'],
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    return (
        <div style={{ '--sticky-offset': '48px' }}>
            <PaginatedUserList
                searchLabel={string.SEARCH_STUDENTS()}
                onSearch={debouncedSetQuery()}
                page={search().page}
                data={membersQuery.data}
                onPageChange={page => navigate({ search: { ...search(), page } })}
                onPagePreload={page => qc.prefetchQuery(groupMembersQueryOptions(client, groupId(), page))}
                onRefresh={() => qc.invalidateQueries({ queryKey: ['groups', groupId(), 'members'] })}
                onClick={user =>
                    navigate({
                        to: '/users/$userId',
                        params: { userId: user.id },
                    })
                }
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

    const managersQuery = createQuery(() => ({
        ...groupManagersQueryOptions(client, groupId(), search().page, query()),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data'],
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    return (
        <div style={{ '--sticky-offset': '48px' }}>
            <PaginatedUserList
                searchLabel={string.SEARCH_TEACHERS()}
                onSearch={debouncedSetQuery()}
                page={search().page}
                data={managersQuery.data}
                onPageChange={page => navigate({ search: { ...search(), page } })}
                onPagePreload={page => qc.prefetchQuery(groupManagersQueryOptions(client, groupId(), page))}
                onRefresh={() => qc.invalidateQueries({ queryKey: ['groups', groupId(), 'managers'] })}
                onClick={user =>
                    navigate({
                        to: '/users/$userId',
                        params: { userId: user.id },
                    })
                }
            />
        </div>
    )
}
