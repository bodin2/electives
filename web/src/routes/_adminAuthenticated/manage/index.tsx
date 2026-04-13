import ClassIcon from '@iconify-icons/mdi/class'
import SettingsIcon from '@iconify-icons/mdi/cog'
import PeopleIcon from '@iconify-icons/mdi/people'
import TeamIcon from '@iconify-icons/mdi/people-group'
import TeacherIcon from '@iconify-icons/mdi/teacher'
import { createFileRoute } from '@tanstack/solid-router'
import { Icon } from 'm3-solid'
import { createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../../components/Button'
import LogOutButton from '../../../components/buttons/LogOutButton'
import SettingsDialog from '../../../components/dialogs/SettingsDialog'
import { LinkCard } from '../../../components/LinkCard'
import Page from '../../../components/Page'
import { HStack, VStack } from '../../../components/Stack'
import IconLabel from '../../../components/subjects/IconLabel'
import { useI18n } from '../../../providers/I18nProvider'
import styles from './index.module.css'
import type { IconifyIcon } from '@iconify/types'
import type { RoutePath } from '../../../main'

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
            <VStack class={styles.content} gap={16}>
                <VStack gap={8}>
                    <IconLabel icon={TeamIcon} text={string.USERS()} class={styles.label} />
                    <VStack gap={16} class={styles.cardGrid}>
                        <GridCard icon={PeopleIcon} title={string.STUDENTS()} to="/manage/students" />
                        <GridCard icon={TeacherIcon} title={string.TEACHER()} to="/manage/teachers" />
                        <GridCard icon={TeamIcon} title={string.TEAMS()} to="/manage/teams" />
                    </VStack>
                </VStack>
                <VStack gap={8}>
                    <IconLabel icon={ClassIcon} text={string.CLASSES()} class={styles.label} />
                    <VStack gap={16} class={styles.cardGrid}>
                        <GridCard icon={ClassIcon} title={string.ELECTIVES()} to="/manage/electives" />
                        <GridCard icon={ClassIcon} title={string.SUBJECTS()} to="/manage/subjects" />
                    </VStack>
                </VStack>
            </VStack>
            <Portal>
                <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
            </Portal>
        </Page>
    )
}

function GridCard(props: { title: string; icon: IconifyIcon; to: RoutePath }) {
    return (
        <LinkCard variant="filled" class={styles.card} to={props.to}>
            <Icon icon={props.icon} class={styles.icon} />
            <h1 class="m3-title-large">{props.title}</h1>
        </LinkCard>
    )
}
