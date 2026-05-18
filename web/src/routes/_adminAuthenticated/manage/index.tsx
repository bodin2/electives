import ClassIcon from '@iconify-icons/mdi/class'
import PeopleIcon from '@iconify-icons/mdi/people'
import TeamIcon from '@iconify-icons/mdi/people-group'
import TeacherIcon from '@iconify-icons/mdi/teacher'
import TicketIcon from '@iconify-icons/mdi/ticket'
import { createFileRoute } from '@tanstack/solid-router'
import { Icon } from 'm3-solid/src'
import IconLabel from '~/components/IconLabel'
import { LinkCard } from '~/components/LinkCard'
import Page from '~/components/Page'
import { VStack } from '~/components/Stack'
import { useI18n } from '~/providers/I18nProvider'
import styles from './index.module.css'
import type { IconifyIcon } from '@iconify/types'
import type { RoutePath } from '~/main'

export const Route = createFileRoute('/_adminAuthenticated/manage/')({
    component: AdminDashboard,
})

function AdminDashboard() {
    const { string } = useI18n()

    return (
        <Page name={string.ADMIN_DASHBOARD()} leading={null} trailing={null}>
            <VStack class={styles.content} gap={16}>
                <VStack gap={8}>
                    <IconLabel icon={TeamIcon} text={string.USERS()} class={styles.label} />
                    <VStack gap={8} class={styles.cardGrid}>
                        <GridCard icon={PeopleIcon} title={string.STUDENTS()} to="/manage/students" />
                        <GridCard icon={TeacherIcon} title={string.TEACHERS()} to="/manage/teachers" />
                        <GridCard icon={TeamIcon} title={string.GROUPS()} to="/manage/groups" />
                    </VStack>
                </VStack>
                <VStack gap={8}>
                    <IconLabel icon={ClassIcon} text={string.CLASSES()} class={styles.label} />
                    <VStack gap={8} class={styles.cardGrid}>
                        <GridCard icon={TicketIcon} title={string.ENROLLMENTS()} to="/manage/enrollments" />
                        <GridCard icon={ClassIcon} title={string.SUBJECTS()} to="/manage/subjects" />
                    </VStack>
                </VStack>
            </VStack>
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
