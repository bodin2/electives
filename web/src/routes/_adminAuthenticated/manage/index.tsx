import SettingsIcon from '@iconify-icons/mdi/cog'
import { createFileRoute } from '@tanstack/solid-router'
import { Card } from 'm3-solid'
import { createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../../components/Button'
import LogOutButton from '../../../components/buttons/LogOutButton'
import SettingsDialog from '../../../components/dialogs/SettingsDialog'
import Page from '../../../components/Page'
import { HStack, VStack } from '../../../components/Stack'
import { useI18n } from '../../../providers/I18nProvider'

export const Route = createFileRoute('/_adminAuthenticated/manage/')({
    component: AdminDashboard,
})

function AdminDashboard() {
    const { string } = useI18n()
    const [settingsOpen, setSettingsOpen] = createSignal(false)

    return (
        <Page
            name={string.ADMIN_DASHBOARD()}
            trailing={
                <HStack>
                    <LogOutButton />
                    <Button
                        variant="text"
                        aria-label={string.SETTINGS()}
                        icon={SettingsIcon}
                        iconType="only"
                        onClick={() => {
                            setSettingsOpen(true)
                        }}
                    />
                </HStack>
            }
        >
            <VStack gap={16} style={{ padding: '24px' }}>
                <Card variant="filled">
                    <h1>work in progress!!!</h1>
                </Card>
            </VStack>
            <Portal>
                <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
            </Portal>
        </Page>
    )
}
