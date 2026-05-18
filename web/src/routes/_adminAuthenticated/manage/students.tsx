import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery, keepPreviousData, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createMemo, createSignal } from 'solid-js'
import { UserType } from '~/api/types'
import PaginatedUserList, { type PaginatedUserListHandle } from '~/components/admin/PaginatedUserList'
import LinkButton from '~/components/LinkButton'
import Page from '~/components/Page'
import { HStack, VStack } from '~/components/Stack'
import { BulkAddUserAction } from '~/components/users/BulkAddUserAction'
import { useUserDisplayContext } from '~/components/users/UserDisplayContext'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { studentsQueryOptions } from '~/queries/users'
import { debounce } from '~/utils'

export const Route = createFileRoute('/_adminAuthenticated/manage/students')({
    validateSearch: (search: Record<string, unknown>) => ({
        page: Math.max(Number(search?.page ?? 1), 1),
    }),
    loaderDeps: ({ search }) => ({ page: search.page }),
    loader: async ({ context: { client, queryClient }, deps: { page } }) => {
        await queryClient.ensureQueryData(studentsQueryOptions(client, page))
    },
    component: RouteComponent,
})

function RouteComponent() {
    const { string } = useI18n()
    const { client } = useAPI()
    const navigate = Route.useNavigate()
    const search = Route.useSearch()
    const userDisplayContext = useUserDisplayContext()
    const qc = useQueryClient()

    const [query, setQuery] = createSignal<string | undefined>()
    const studentsQuery = createQuery(() => ({
        ...studentsQueryOptions(client, search().page, query()),
        placeholderData: keepPreviousData,
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    let listHandle: PaginatedUserListHandle | undefined

    const onRefresh = () => qc.invalidateQueries({ queryKey: ['admin', 'students'] })

    return (
        <Page name={string.STUDENTS()} leading={null} trailing={null}>
            <PaginatedUserList
                onSearch={debouncedSetQuery()}
                searchLabel={string.SEARCH_STUDENTS()}
                headerRight={() => (
                    <HStack gap={8}>
                        <BulkAddUserAction type={UserType.STUDENT} onComplete={onRefresh} />
                        <LinkButton size="xs" {...userDisplayContext.createLinkProps('student')} icon={PlusIcon}>
                            {string.ADD_STUDENT()}
                        </LinkButton>
                    </HStack>
                )}
                ref={h => (listHandle = h)}
                page={search().page}
                data={studentsQuery.data ?? { users: [], total: 0 }}
                onPageChange={page => navigate({ search: { page } })}
                onPagePreload={page => qc.prefetchQuery(studentsQueryOptions(client, page))}
                onRefresh={onRefresh}
                onClick={user => navigate(userDisplayContext.editLinkProps(user.id))}
                emptyElement={
                    <VStack grow alignHorizontal="center" alignVertical="center" gap={16}>
                        <VStack alignHorizontal="center">
                            <h1 class="m3-headline-medium text-balance">{string.NO_STUDENTS_HINT()}</h1>
                            <p class="m3-body-large text-surface-variant text-center text-balance text-ws-pre-line">
                                {string.NO_STUDENTS_HINT_DESCRIPTION()}
                            </p>
                        </VStack>
                        <HStack gap={12}>
                            <LinkButton
                                size="m"
                                variant="filled"
                                icon={PlusIcon}
                                {...userDisplayContext.createLinkProps('student')}
                            >
                                {string.ADD_STUDENT()}
                            </LinkButton>
                            {/* TODO: Bulk add hint */}
                        </HStack>
                    </VStack>
                }
            />
        </Page>
    )
}
