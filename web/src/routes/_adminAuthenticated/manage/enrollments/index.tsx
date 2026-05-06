import MagnifyIcon from '@iconify-icons/mdi/magnify'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute, useNavigate } from '@tanstack/solid-router'
import { TextField } from 'm3-solid'
import { createMemo, createSignal, For, Show } from 'solid-js'
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
    }))

    const filteredElectives = createMemo(() => {
        const query = search().toLowerCase()
        return (electivesQuery.data ?? []).filter(e => e.name.toLowerCase().includes(query))
    })

    const handle = () => {
        navigate({
            to: '/manage/enrollments/$enrollmentId',
            params: { enrollmentId: 'new' },
        })
    }

    return (
        <Page name={string.ENROLLMENTS()} leading={null} trailing={null}>
            <VStack gap={16} class="padded">
                <HStack alignVertical="center" gap={16} wrap>
                    <div class={styles.searchContainer}>
                        <TextField
                            leadingIcon={MagnifyIcon}
                            label={string.SEARCH_ENROLLMENTS()}
                            variant="filled"
                            class={styles.search}
                            placeholder={string.SEARCH_ENROLLMENTS()}
                            onInput={e => setSearch(e.target.value)}
                        />
                    </div>
                    <Button variant="filled" icon={PlusIcon} onClick={handle}>
                        {string.CREATE_ENROLLMENT()}
                    </Button>
                </HStack>
                <SuspenseLoadingPage>
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
                </SuspenseLoadingPage>
            </VStack>
        </Page>
    )
}
