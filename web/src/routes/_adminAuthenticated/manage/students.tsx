import PlusIcon from '@iconify-icons/mdi/plus'
import { createFileRoute, useRouter } from '@tanstack/solid-router'
import PaginatedUserList, { type PaginatedUserListHandle } from '../../../components/admin/PaginatedUserList'
import LinkButton from '../../../components/LinkButton'
import Page from '../../../components/Page'
import { useUserDisplayContext } from '../../../components/users/UserDisplayContext'
import { useI18n } from '../../../providers/I18nProvider'
import { Route as UserIdRoute } from './users/$userId'

export const Route = createFileRoute('/_adminAuthenticated/manage/students')({
    validateSearch: (search: Record<string, unknown>) => ({
        page: Math.max(Number(search?.page ?? 1), 1),
    }),
    loaderDeps: ({ search }) => ({ page: search.page }),
    loader: async ({ context: { client }, deps: { page } }) => {
        return await client.users.admin.fetchStudents(page)
    },
    component: RouteComponent,
})

function RouteComponent() {
    const { string } = useI18n()
    const router = useRouter()
    const navigate = Route.useNavigate()
    const search = Route.useSearch()
    const data = Route.useLoaderData()
    const userDisplayContext = useUserDisplayContext()

    let listHandle: PaginatedUserListHandle | undefined

    return (
        <Page name={string.STUDENTS()} leading={null} trailing={null}>
            {/* TODO: Search */}
            <PaginatedUserList
                headerRight={() => (
                    <LinkButton size="xs" {...userDisplayContext.createLinkProps('student')} icon={PlusIcon}>
                        {string.ADD_STUDENT()}
                    </LinkButton>
                )}
                ref={h => (listHandle = h)}
                page={search().page}
                data={data()}
                onPageChange={page => navigate({ search: { page } })}
                onPagePreload={page => router.preloadRoute({ to: Route.fullPath, search: { page } })}
                onRefresh={() =>
                    router.invalidate({
                        filter: r => r.routeId === Route.id || r.routeId === UserIdRoute.id,
                        sync: true,
                    })
                }
                onClick={user => navigate(userDisplayContext.editLinkProps(user.id))}
            />
        </Page>
    )
}
