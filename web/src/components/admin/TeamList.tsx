import PlusIcon from '@iconify-icons/mdi/plus'
import { TextField } from 'm3-solid'
import { createMemo, createSignal, For } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { HStack, VStack } from '../Stack'
import TeamItem from './TeamItem'
import styles from './TeamList.module.css'
import type { Team } from '../../api/structures'

interface TeamListProps {
    teams: Team[]
    memberCounts: Record<number, number>
    onEdit: (team: Team) => void
    onCreate: () => void
    onDelete: (team: Team) => Promise<void>
}

export default function TeamList(props: TeamListProps) {
    const { string } = useI18n()
    const [search, setSearch] = createSignal('')
    // So the dialog can exit without changing the content
    const [deletingTeam, setDeletingTeam] = createSignal(false)
    let teamToDelete: Team | undefined

    const filteredTeams = createMemo(() => {
        const query = search().toLowerCase()
        return props.teams
            .filter(t => t.name.toLowerCase().includes(query))
            .sort((a, b) => a.name.localeCompare(b.name))
    })

    const setTeamToDelete = (team: Team | undefined) => {
        teamToDelete = team
        setDeletingTeam(!!team)
    }

    return (
        <VStack class={styles.container} gap={16}>
            <HStack alignVertical="center" gap={16} wrap>
                <div class={styles.searchContainer}>
                    <TextField
                        label={string.SEARCH_TEAMS()}
                        variant="filled"
                        class={styles.search}
                        placeholder={string.SEARCH_TEAMS()}
                        value={search()}
                        onInput={e => setSearch(e.target.value)}
                    />
                </div>
                <Button variant="filled" icon={PlusIcon} onClick={props.onCreate}>
                    {string.CREATE_TEAM()}
                </Button>
            </HStack>

            <VStack gap={0} class={styles.list}>
                <For each={filteredTeams()}>
                    {team => (
                        <TeamItem
                            team={team}
                            onEdit={props.onEdit}
                            onDelete={t => setTeamToDelete(t)}
                            memberCount={props.memberCounts[team.id] ?? 0}
                        />
                    )}
                </For>
            </VStack>

            <Dialog
                open={deletingTeam()}
                closedBy="any"
                onClose={() => setTeamToDelete(undefined)}
                headline={string.DELETE_TEAM()}
                actions={
                    <HStack slot="actions" gap={8}>
                        <Button variant="text" onClick={() => setTeamToDelete(undefined)}>
                            {string.CANCEL()}
                        </Button>
                        <Button
                            variant="tonal-error"
                            onClick={async () => {
                                if (teamToDelete) await props.onDelete(teamToDelete)
                                setTeamToDelete(undefined)
                            }}
                        >
                            {string.DELETE_TEAM()}
                        </Button>
                    </HStack>
                }
            >
                <p>{string.CONFIRM_DELETE_TEAM({ name: teamToDelete?.name ?? '' })}</p>
            </Dialog>
        </VStack>
    )
}
