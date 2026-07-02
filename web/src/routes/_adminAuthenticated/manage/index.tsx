import ClassIcon from '@iconify-icons/mdi/class'
import ClockIcon from '@iconify-icons/mdi/clock'
import PeopleIcon from '@iconify-icons/mdi/people'
import TeamIcon from '@iconify-icons/mdi/people-group'
import TeacherIcon from '@iconify-icons/mdi/teacher'
import TicketIcon from '@iconify-icons/mdi/ticket'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute, useNavigate } from '@tanstack/solid-router'
import { Icon } from 'm3-solid/src'
import { For, Show } from 'solid-js'
import AdminEnrollmentCard from '~/components/enrollments/AdminEnrollmentCard'
import IconLabel from '~/components/IconLabel'
import { LinkCard } from '~/components/LinkCard'
import Page from '~/components/Page'
import { VStack } from '~/components/Stack'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { adminEnrollmentsProgressQueryOptions, enrollmentsQueryOptions } from '~/queries/enrollments'
import { groupsQueryOptions } from '~/queries/groups'
import { enrollmentSorter } from '~/utils'
import styles from './index.module.css'
import type { IconifyIcon } from '@iconify/types'
import type { RoutePath } from '~/main'

const ONE_HOUR_MS = 60 * 60 * 1000

export const Route = createFileRoute('/_adminAuthenticated/manage/')({
    component: AdminDashboard,
    loader: async ({ context: { client, queryClient } }) => {
        await Promise.all([
            queryClient.ensureQueryData(enrollmentsQueryOptions(client)),
            queryClient.ensureQueryData(groupsQueryOptions(client)),
        ])
    },
})

function AdminDashboard() {
    const { string } = useI18n()
    const { client } = useAPI()
    const navigate = useNavigate()

    const enrollmentsQuery = createQuery(() => ({
        ...enrollmentsQueryOptions(client),
        select: data => data.sort(enrollmentSorter),
    }))

    const activeEnrollments = () => {
        const all = enrollmentsQuery.data ?? []
        return all.filter(e => {
            if (e.isSelectionEnded()) return false
            const t = e.getTimeUntilOpen()
            return t !== null && t < ONE_HOUR_MS
        })
    }

    const activeIds = () => activeEnrollments().map(e => e.id)

    const progressQuery = createQuery(() => adminEnrollmentsProgressQueryOptions(client, activeIds()))

    const goToEnrollment = (id: number) =>
        navigate({
            to: '/manage/enrollments/$enrollmentId',
            params: { enrollmentId: String(id) },
        })

    return (
        <Page name={string.ADMIN_DASHBOARD()}>
            <VStack class={styles.content} gap={16}>
                <Show when={activeEnrollments().length > 0}>
                    <VStack gap={8}>
                        <IconLabel icon={ClockIcon} text={string.ACTIVE_ENROLLMENTS()} class={styles.label} />
                        <div class={styles.enrollmentGrid}>
                            <For each={activeEnrollments()}>
                                {enrollment => (
                                    <AdminEnrollmentCard
                                        enrollment={enrollment}
                                        progress={progressQuery.data?.[enrollment.id]}
                                        onClick={goToEnrollment}
                                    />
                                )}
                            </For>
                        </div>
                    </VStack>
                </Show>
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
