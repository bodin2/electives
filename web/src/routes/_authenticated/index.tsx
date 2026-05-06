import SettingsIcon from '@iconify-icons/mdi/cog'
import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../components/Button'
import SettingsDialog from '../../components/dialogs/SettingsDialog'
import ElectiveList from '../../components/electives/ElectiveList'
import UserInfoCard from '../../components/electives/UserInfoCard'
import Page from '../../components/Page'
import { VStack } from '../../components/Stack'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { electivesQueryOptions } from '../../queries/electives'
import { selectionsQueryOptions } from '../../queries/selections'
import { electiveSorter, nonNull } from '../../utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../_authenticated'
import styles from './index.module.css'

export const Route = createFileRoute('/_authenticated/')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    loader: async ({ context }) => {
        const { client, queryClient } = context
        await Promise.all([
            queryClient.ensureQueryData(electivesQueryOptions(client)),
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

    const electivesQuery = createQuery(() => electivesQueryOptions(client))

    const electives = () =>
        (electivesQuery.data ?? [])
            .filter(e => {
                // Teachers can see all electives
                if (user.isTeacher()) return true
                if (e.teamId != null) return user.hasTeam(e.teamId)
                return true
            })
            .sort(electiveSorter)

    const [settingsOpen, setSettingsOpen] = createSignal(false)

    const onCardClick = (id: number) => {
        navigate({
            to: '/enroll/$electiveId',
            params: { electiveId: id },
        })
    }

    return (
        <Page
            name={string.ELECTIVES()}
            trailing={
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
            <ElectiveList electives={electives()} user={user} onCardClick={onCardClick} />
            <Portal>
                <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
            </Portal>
        </Page>
    )
}
