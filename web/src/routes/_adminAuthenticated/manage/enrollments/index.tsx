import MagnifyIcon from '@iconify-icons/mdi/magnify'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute, useNavigate } from '@tanstack/solid-router'
import { TextField } from 'm3-solid'
import { createSignal, For, Show } from 'solid-js'
import { Button } from '../../../../components/Button'
import AdminElectiveCard from '../../../../components/electives/AdminElectiveCard'
import Page from '../../../../components/Page'
import { SuspenseLoadingPage } from '../../../../components/pages/LoadingPage'
import { HStack, VStack } from '../../../../components/Stack'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { electivesQueryOptions } from '../../../../queries/electives'
import { teamsQueryOptions } from '../../../../queries/teams'
import { electiveSorter } from '../../../../utils'
import styles from './index.module.css'

export const Route = createFileRoute('/_adminAuthenticated/manage/enrollments/')({
    component: RouteComponent,
    loader: async ({ context: { client, queryClient } }) => {
        await Promise.all([
            queryClient.ensureQueryData(electivesQueryOptions(client)),
            queryClient.ensureQueryData(teamsQueryOptions(client)),
        ])
    },
})

function RouteComponent() {
    const { string } = useI18n()
    const { client } = useAPI()
    const navigate = useNavigate()
    const [search, setSearch] = createSignal('')

    const electivesQuery = createQuery(() => ({
        ...electivesQueryOptions(client),
        select: data => data.sort(electiveSorter),
        notifyOnChangeProps: ['data'],
    }))

    const electives = () => electivesQuery.data ?? []

    const filteredElectives = () => {
        const query = search().toLowerCase()
        return electives().filter(e => e.name.toLowerCase().includes(query))
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
                        when={electives().length > 0}
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
                        <Show
                            when={filteredElectives().length > 0}
                            fallback={<p class="text-surface-variant">{string.NO_RESULTS_FOUND()}</p>}
                        >
                            <For each={filteredElectives()}>
                                {elective => (
                                    <AdminElectiveCard
                                        elective={elective}
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
                    </Show>
                </SuspenseLoadingPage>
            </VStack>
        </Page>
    )
}
