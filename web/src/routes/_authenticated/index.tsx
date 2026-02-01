import SettingsIcon from '@iconify-icons/mdi/cog'
import { createFileRoute } from '@tanstack/solid-router'
import { createSignal, For } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../components/Button'
import SettingsDialog from '../../components/dialogs/SettingsDialog'
import ElectiveCard from '../../components/electives/ElectiveCard'
import UserInfoCard from '../../components/electives/UserInfoCard'
import Page from '../../components/Page'
import { HStack, VStack } from '../../components/Stack'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { electiveSorter, nonNull } from '../../utils'
import styles from './index.module.css'

export const Route = createFileRoute('/_authenticated/')({
    loader: ({ context }) => context.client.electives.fetchAll().then(electives => electives.sort(electiveSorter)),
    component: Home,
})

function Home() {
    const api = useAPI()
    const { string } = useI18n()
    const data = Route.useLoaderData()
    const electives = () =>
        data().filter(e => {
            // Teachers can see all electives
            if (nonNull(api.client.user).isTeacher()) return true

            if (e.teamId != null) return nonNull(api.client.user).hasTeam(e.teamId)
            return true
        })
    const [settingsOpen, setSettingsOpen] = createSignal(false)

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
                <UserInfoCard cardClass={styles.card} avatarClass={styles.avatar} />
            </VStack>
            <HStack gap={16} class="padded" wrap>
                <For each={electives()}>
                    {elective => <ElectiveCard elective={elective} cardClass={styles.electiveCard} />}
                </For>
            </HStack>
            <Portal>
                <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
            </Portal>
        </Page>
    )
}
