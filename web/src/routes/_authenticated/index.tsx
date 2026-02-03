import SettingsIcon from '@iconify-icons/mdi/cog'
import { createFileRoute } from '@tanstack/solid-router'
import { createSignal, For, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../components/Button'
import SettingsDialog from '../../components/dialogs/SettingsDialog'
import ElectiveCard from '../../components/electives/ElectiveCard'
import UserInfoCard from '../../components/electives/UserInfoCard'
import Page from '../../components/Page'
import { HStack, VStack } from '../../components/Stack'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { electiveSorter, groupItems, nonNull } from '../../utils'
import styles from './index.module.css'

export const Route = createFileRoute('/_authenticated/')({
    loader: async ({ context }) => {
        const user = nonNull(context.client.user)

        const [electives] = await Promise.all([
            context.client.electives.fetchAll().then(electives =>
                electives
                    .filter(e => {
                        // Teachers can see all electives
                        if (user.isTeacher()) return true

                        if (e.teamId != null) return user.hasTeam(e.teamId)
                        return true
                    })
                    .sort(electiveSorter),
            ),
            // Prefetch student's selections
            user.isStudent() ? context.client.selections.fetch('@me') : null,
        ])

        return { electives, user }
    },
    component: Home,
})

function Home() {
    const api = useAPI()
    const { string } = useI18n()
    const data = Route.useLoaderData()

    const [settingsOpen, setSettingsOpen] = createSignal(false)

    const electives = () =>
        groupItems(data().electives, elective => {
            if (api.client.selections.resolveSelection(data().user.id, elective.id)) return 'selected' as const
            return 'unselected' as const
        })

    return (
        <Page
            name={string.ELECTIVES()}
            leading={
                <Button
                    variant="text"
                    aria-label={string.SETTINGS()}
                    icon={SettingsIcon}
                    iconType="only"
                    onClick={() => {
                        setSettingsOpen(true)
                    }}
                />
            }
        >
            <VStack gap={16} class="padded">
                <UserInfoCard class={styles.card} />
            </VStack>
            <Show when={electives().unselected?.length}>
                <HStack gap={16} class="padded" wrap>
                    <For each={electives().unselected}>
                        {elective => <ElectiveCard elective={elective} class={styles.card} />}
                    </For>
                </HStack>
            </Show>
            <Show when={electives().selected?.length}>
                <HStack gap={16} class="padded" wrap>
                    <For each={electives().selected}>
                        {elective => <ElectiveCard elective={elective} class={styles.card} />}
                    </For>
                </HStack>
            </Show>
            <Portal>
                <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
            </Portal>
        </Page>
    )
}
