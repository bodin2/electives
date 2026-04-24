import { createFileRoute, useNavigate, useRouter } from '@tanstack/solid-router'
import PaginatedUserList, { type PaginatedUserListHandle } from '../../../components/admin/PaginatedUserList'
import Page from '../../../components/Page'
import { useI18n } from '../../../providers/I18nProvider'

type UserSearch = {
    page: number
}

export const Route = createFileRoute('/_adminAuthenticated/manage/students')({
    validateSearch: (search: Record<string, unknown>): UserSearch => {
        return {
            page: Number(search?.page ?? 1) || 1,
        }
    },
    loaderDeps: ({ search: { page } }) => ({ page }),
    loader: async ({ context, deps: { page } }) => {
        return await context.client.users.admin.fetchStudents(page)
    },
    component: RouteComponent,
})

function RouteComponent() {
    const { string } = useI18n()
    const navigate = useNavigate({ from: Route.fullPath })
    const search = Route.useSearch()
    const data = Route.useLoaderData()
    const router = useRouter()

    let listHandle: PaginatedUserListHandle | undefined

    return (
        <Page name={string.STUDENTS()} leading={null} trailing={null}>
            {/* TODO: Search */}
            <PaginatedUserList
                ref={h => (listHandle = h)}
                page={search().page}
                data={data()}
                onPageChange={page => navigate({ search: { page } })}
                onRefresh={() => router.invalidate()}
                onClick={user => {
                    console.log('Clicked student:', user.fullName)
                    // @TODO: Open edit dialog
                }}
            />
        </Page>
    )
}
