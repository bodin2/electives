import AccountGroupIcon from '@iconify-icons/mdi/account-group-outline'
import { Icon } from 'm3-solid'
import { createSignal, For } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { Option, Select } from '../Select'
import { VStack } from '../Stack'
import type { Team } from '../../api'

export default function AddTeamToStudentDialog(props: {
    open: boolean
    onClose: (picked: Team | null) => unknown
    teams: Team[]
    currentTeamIds: number[]
}) {
    const { string } = useI18n()

    const [team, setTeam] = createSignal<Team | null>(null)

    let form!: HTMLFormElement

    return (
        <Dialog
            quick
            onClose={() => props.onClose(null)}
            open={props.open}
            headline={<h1 class="m3-headline-small">{string.ADD_STUDENT_TO_TEAM()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={AccountGroupIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }} ref={form}>
                    <Button
                        variant="text"
                        onClick={() => {
                            setTeam(null)
                            form.submit()
                        }}
                    >
                        {string.CANCEL()}
                    </Button>
                    <Button
                        variant="text"
                        disabled={!team()}
                        onClick={() => {
                            props.onClose(team())
                            form.submit()
                        }}
                    >
                        {string.ADD()}
                    </Button>
                </form>
            }
        >
            <VStack
                gap={0}
                as="form"
                onSubmit={e => {
                    e.preventDefault()
                    if (team()) {
                        props.onClose(team())
                        form.submit()
                    }
                }}
            >
                <Select
                    label={string.TEAMS()}
                    value={team()?.id ?? ''}
                    onInput={e => setTeam(props.teams.find(t => t.id === Number(e.currentTarget.value)) || null)}
                >
                    <Option value="" hidden selected>
                        {string.SELECT_TEAM_HINT()}
                    </Option>
                    <For each={props.teams.filter(t => !props.currentTeamIds.includes(t.id))}>
                        {team => <Option value={team.id}>{team.name}</Option>}
                    </For>
                </Select>
            </VStack>
        </Dialog>
    )
}
