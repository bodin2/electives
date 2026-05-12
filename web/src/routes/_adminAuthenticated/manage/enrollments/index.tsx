import MagnifyIcon from '@iconify-icons/mdi/magnify'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute, useNavigate } from '@tanstack/solid-router'
import { TextField } from 'm3-solid/src'
import { createSignal, For, Show } from 'solid-js'
import { Button } from '../../../../components/Button'
import AdminEnrollmentCard from '../../../../components/enrollments/AdminEnrollmentCard'
import Page from '../../../../components/Page'
import { SuspenseLoadingPage } from '../../../../components/pages/LoadingPage'
import { HStack, VStack } from '../../../../components/Stack'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { enrollmentsQueryOptions } from '../../../../queries/enrollments'
import { groupsQueryOptions } from '../../../../queries/groups'
import { enrollmentSorter } from '../../../../utils'
import styles from './index.module.css'

export const Route = createFileRoute('/_adminAuthenticated/manage/enrollments/')({
    component: RouteComponent,
    loader: async ({ context: { client, queryClient } }) => {
        await Promise.all([
            queryClient.ensureQueryData(enrollmentsQueryOptions(client)),
            queryClient.ensureQueryData(groupsQueryOptions(client)),
        ])
    },
})

function RouteComponent() {
    const { string } = useI18n()
    const { client } = useAPI()
    const navigate = useNavigate()
    const [search, setSearch] = createSignal('')

    const enrollmentsQuery = createQuery(() => ({
        ...enrollmentsQueryOptions(client),
        select: data => data.sort(enrollmentSorter),
        notifyOnChangeProps: ['data'],
    }))

    const enrollments = () => enrollmentsQuery.data ?? []

    const filteredEnrollments = () => {
        const query = search().toLowerCase()
        return enrollments().filter(e => e.name.toLowerCase().includes(query))
    }

    const handleCreate = () => {
        navigate({
            to: '/manage/enrollments/$enrollmentId',
            params: { enrollmentId: 'new' },
        })
    }

    return (
        <Page name={string.ENROLLMENTS()} leading={null} trailing={null}>
            <VStack gap={0} grow>
                <HStack class={styles.searchContainer} alignVertical="center" gap={16} wrap>
                    <TextField
                        leadingIcon={MagnifyIcon}
                        label={string.SEARCH_ENROLLMENTS()}
                        variant="filled"
                        class={styles.search}
                        placeholder={string.SEARCH_ENROLLMENTS()}
                        onInput={e => setSearch(e.target.value)}
                    />
                    <Button variant="filled" icon={PlusIcon} onClick={handleCreate}>
                        {string.CREATE_ENROLLMENT()}
                    </Button>
                </HStack>
                <SuspenseLoadingPage debugName="AdminEnrollmentsList">
                    <Show
                        when={enrollments().length > 0}
                        fallback={
                            <VStack grow alignHorizontal="center" alignVertical="center" gap={16}>
                                <VStack alignHorizontal="center">
                                    <h1 class="m3-headline-medium text-balance">{string.NO_ENROLLMENTS_HINT()}</h1>
                                    <p class="m3-body-large text-surface-variant text-center text-balance">
                                        {string.NO_ENROLLMENTS_HINT_DESCRIPTION()}
                                    </p>
                                </VStack>
                                <Button size="m" variant="filled" icon={PlusIcon} onClick={handleCreate}>
                                    {string.CREATE_ENROLLMENT()}
                                </Button>
                            </VStack>
                        }
                    >
                        <VStack style={{ 'padding-inline': '16px' }} gap={16}>
                            <Show
                                when={filteredEnrollments().length > 0}
                                fallback={<p class="text-surface-variant">{string.NO_RESULTS_FOUND()}</p>}
                            >
                                <For each={filteredEnrollments()}>
                                    {enrollment => (
                                        <AdminEnrollmentCard
                                            enrollment={enrollment}
                                            onClick={id =>
                                                navigate({
                                                    to: '/manage/enrollments/$enrollmentId',
                                                    params: { enrollmentId: String(id) },
                                                })
                                            }
                                        />
                                    )}
                                </For>
                            </Show>
                        </VStack>
                    </Show>
                </SuspenseLoadingPage>
            </VStack>
        </Page>
    )
}
