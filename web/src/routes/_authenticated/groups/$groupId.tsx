import { createQuery, keepPreviousData, skipToken, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createMemo, createSignal, Match, Switch } from 'solid-js'
import { NotFoundError, UnauthorizedError } from '~/api/types'
import PaginatedUserList from '~/components/admin/PaginatedUserList'
import { GroupMembersFilterChip } from '~/components/enrollments/GroupMembersFilterChip'
import Page from '~/components/Page'
import { SuspenseLoadingPage } from '~/components/pages/LoadingPage'
import NotFoundPage from '~/components/pages/NotFoundPage'
import StickyTabs from '~/components/StickyTabs'
import { useUserDisplayContext } from '~/components/users/UserDisplayContext'
import { useTabPersistence } from '~/hooks/useTabPersistence'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentsQueryOptions, enrollmentUnenrolledMembersQueryOptions } from '~/queries/enrollments'
import { groupManagersQueryOptions, groupMembersQueryOptions, groupQueryOptions } from '~/queries/groups'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '~/routes/_authenticated'
import { debounce, nonNull } from '~/utils'
import { catchErrors } from '~/utils/error-component'

export const Route = createFileRoute('/_authenticated/groups/$groupId')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
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
    const userDisplayContext = useUserDisplayContext()

    const [query, setQuery] = createSignal<string | undefined>(undefined)
    const [filterEnrollmentId, setFilterEnrollmentId] = createSignal<number | null>(null)

    const isFiltered = () => filterEnrollmentId() !== null

    const membersQuery = createQuery(() => ({
        ...groupMembersQueryOptions(client, groupId(), search().page, query()),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data', 'isFetching'],
        enabled: filterEnrollmentId() === null,
    }))
    const unenrolledQuery = createQuery(() => ({
        ...enrollmentUnenrolledMembersQueryOptions(
            client,
            filterEnrollmentId() ?? 0,
            filterEnrollmentId() === null ? skipToken : groupId(),
            search().page,
        ),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data', 'isFetching'],
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    const activeData = () => (filterEnrollmentId() === null ? membersQuery.data : unenrolledQuery.data)

    return (
        <div style={{ '--sticky-offset': '48px' }}>
            <PaginatedUserList
                isFetching={membersQuery.isFetching || unenrolledQuery.isFetching}
                searchLabel={string.SEARCH_STUDENTS()}
                // The unenrolled-members endpoint doesn't support server-side search
                onSearch={isFiltered() ? undefined : debouncedSetQuery}
                page={search().page}
                data={activeData()}
                onPageChange={page => navigate({ search: { ...search(), page } })}
                onPagePreload={page =>
                    filterEnrollmentId() === null && qc.prefetchQuery(groupMembersQueryOptions(client, groupId(), page))
                }
                onRefresh={() =>
                    filterEnrollmentId() === null
                        ? qc.invalidateQueries({ queryKey: ['groups', groupId(), 'members'] })
                        : qc.invalidateQueries({
                              queryKey: ['enrollments', filterEnrollmentId(), 'unenrolledMembers'],
                          })
                }
                filters={() => (
                    <GroupMembersFilterChip
                        groupId={groupId()}
                        value={filterEnrollmentId()}
                        onChange={v => {
                            setFilterEnrollmentId(v)
                            navigate({ search: { ...search(), page: 1 } })
                        }}
                    />
                )}
                onClick={user => navigate(userDisplayContext.viewLinkProps(user.id))}
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
    const userDisplayContext = useUserDisplayContext()

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
                onClick={user => navigate(userDisplayContext.viewLinkProps(user.id))}
            />
        </div>
    )
}
