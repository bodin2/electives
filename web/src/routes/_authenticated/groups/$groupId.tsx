import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createSignal, Match, Switch } from 'solid-js'
import { NotFoundError, UnauthorizedError } from '~/api/types'
import { GroupManagers } from '~/components/groups/GroupManagers'
import { GroupMembers } from '~/components/groups/GroupMembers'
import Page from '~/components/Page'
import { SuspenseLoadingPage } from '~/components/pages/LoadingPage'
import NotFoundPage from '~/components/pages/NotFoundPage'
import StickyTabs from '~/components/StickyTabs'
import { useTabPersistence } from '~/hooks/useTabPersistence'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentsQueryOptions } from '~/queries/enrollments'
import { groupManagersQueryOptions, groupMembersQueryOptions, groupQueryOptions } from '~/queries/groups'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '~/routes/_authenticated'
import { nonNull } from '~/utils'
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
    const search = Route.useSearch()
    const navigate = Route.useNavigate()
    const { client } = useAPI()
    const { string } = useI18n()

    const groupId = () => Number(params().groupId)

    const groupQuery = createQuery(() => ({
        ...groupQueryOptions(client, groupId()),
    }))

    const [tab, setTab] = createSignal<'members' | 'managers'>('members')
    useTabPersistence(tab, setTab)

    const onPageChange = (page: number) => navigate({ search: { ...search(), page } })

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
                        <GroupMembers groupId={groupId()} page={search().page} onPageChange={onPageChange} />
                    </SuspenseLoadingPage>
                </Match>
                <Match when={tab() === 'managers'}>
                    <SuspenseLoadingPage debugName="ReadOnlyGroupManagers">
                        <GroupManagers groupId={groupId()} page={search().page} onPageChange={onPageChange} />
                    </SuspenseLoadingPage>
                </Match>
            </Switch>
        </Page>
    )
}
