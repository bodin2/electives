import SettingsIcon from '@iconify-icons/mdi/cog'
import { createFileRoute } from '@tanstack/solid-router'
import { createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../components/Button'
import SettingsDialog from '../../components/dialogs/SettingsDialog'
import ElectiveList from '../../components/electives/ElectiveList'
import UserInfoCard from '../../components/electives/UserInfoCard'
import Page from '../../components/Page'
import { VStack } from '../../components/Stack'
import { useI18n } from '../../providers/I18nProvider'
import { electiveSorter, nonNull } from '../../utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../_authenticated'
import styles from './index.module.css'

export const Route = createFileRoute('/_authenticated/')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
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
    const data = Route.useLoaderData()
    const navigate = Route.useNavigate()
    const { string } = useI18n()

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
            <ElectiveList electives={data().electives} user={data().user} onCardClick={onCardClick} />
            <Portal>
                <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
            </Portal>
        </Page>
    )
}
