import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import EnrollmentList from '~/components/enrollments/EnrollmentList'
import Page from '~/components/Page'
import { VStack } from '~/components/Stack'
import UserInfoCard from '~/components/users/UserInfoCard'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentsQueryOptions } from '~/queries/enrollments'
import { selectionsQueryOptions } from '~/queries/selections'
import { enrollmentSorter, nonNull } from '~/utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../_authenticated'
import styles from './index.module.css'

export const Route = createFileRoute('/_authenticated/')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    loader: async ({ context }) => {
        const { client, queryClient } = context
        await Promise.all([
            queryClient.ensureQueryData(enrollmentsQueryOptions(client)),
            nonNull(client.user).isStudent()
                ? queryClient.ensureQueryData(selectionsQueryOptions(client, '@me'))
                : null,
        ])
    },
    component: Home,
})

function Home() {
    const navigate = Route.useNavigate()
    const { string } = useI18n()
    const { client } = useAPI()
    const user = nonNull(client.user)

    const enrollmentsQuery = createQuery(() => enrollmentsQueryOptions(client))

    const enrollments = () =>
        (enrollmentsQuery.data ?? [])
            .filter(e => {
                // Teachers can see all enrollments
                if (user.isTeacher()) return true
                if (e.groupId != null) return user.hasGroup(e.groupId)
                return true
            })
            .sort(enrollmentSorter)

    const onCardClick = (id: number) => {
        navigate({
            to: '/enroll/$enrollmentId',
            params: { enrollmentId: id },
        })
    }

    return (
        <Page name={string.ENROLLMENTS()} allowBacking={false}>
            <VStack gap={16} class="padded">
                <UserInfoCard class={styles.card} />
            </VStack>
            <EnrollmentList enrollments={enrollments()} user={user} onCardClick={onCardClick} />
        </Page>
    )
}
