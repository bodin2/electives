import PeopleIcon from '@iconify-icons/mdi/people-outline'
import { Icon } from 'm3-solid'
import { createSignal, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { Option, Select } from '../Select'
import { HStack, VStack } from '../Stack'
import type { Team } from '../../api'

export interface SelectTeamDialogProps {
    open: boolean
    value: number | null
    onClose: () => unknown
    onSave: (teamId: number | null) => unknown | Promise<unknown>
    teams: Team[]
}

export function SelectTeamDialog(props: SelectTeamDialogProps) {
    const [selected, setSelected] = createSignal(props.value)
    const { string } = useI18n()

    return (
        <Show when={props.open}>
            <Portal>
                <Dialog
                    quick
                    onClose={props.onClose}
                    open
                    headline={<h1 class="m3-headline-small">{string.SELECT_TEAM_HINT()}</h1>}
                    icon={<Icon fill="var(--m3c-secondary)" icon={PeopleIcon} />}
                    centerHeadline
                    actions={
                        <HStack as="form" method="dialog" wrap alignHorizontal="space-between">
                            <Button
                                variant="tonal-error"
                                onClick={async () => {
                                    await props.onSave(null)
                                    props.onClose()
                                }}
                            >
                                {string.RESET()}
                            </Button>
                            <HStack style={{ 'align-self': 'flex-end' }}>
                                <Button variant="text" onClick={props.onClose}>
                                    {string.CANCEL()}
                                </Button>
                                <Button
                                    variant="text"
                                    onClick={async () => {
                                        await props.onSave(selected())
                                        props.onClose()
                                    }}
                                >
                                    {string.SAVE()}
                                </Button>
                            </HStack>
                        </HStack>
                    }
                >
                    <VStack gap={16}>
                        <p class="text-balance text-center">
                            {string.ENROLLMENT_TEAM_HINT({
                                break: (
                                    <>
                                        <br />
                                        <br />
                                    </>
                                ),
                            })}
                        </p>
                        <Select
                            label={string.TEAM()}
                            value={props.value ?? ''}
                            onInput={async e => {
                                const val = e.currentTarget.value
                                const parsed = val ? Number(val) : null
                                setSelected(parsed)
                            }}
                        >
                            <Option value="" hidden selected={props.value === null}>
                                {string.SELECT_TEAM_HINT()}
                            </Option>
                            <Show when={props.teams}>
                                {t =>
                                    t().map(team => (
                                        <Option value={team.id} selected={team.id === props.value}>
                                            {team.name}
                                        </Option>
                                    ))
                                }
                            </Show>
                        </Select>
                    </VStack>
                </Dialog>
            </Portal>
        </Show>
    )
}
