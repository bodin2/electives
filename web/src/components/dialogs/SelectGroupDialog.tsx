import PeopleIcon from '@iconify-icons/mdi/people-outline'
import { Icon } from 'm3-solid/src'
import { createSignal, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { Option, Select } from '../Select'
import { HStack, VStack } from '../Stack'
import type { Group } from '../../api'

export interface SelectGroupDialogProps {
    open: boolean
    value: number | null
    onClose: () => unknown
    onSave: (groupId: number | null) => unknown | Promise<unknown>
    groups: Group[]
}

export function SelectGroupDialog(props: SelectGroupDialogProps) {
    const [selected, setSelected] = createSignal(props.value)
    const { string } = useI18n()

    return (
        <Show when={props.open}>
            <Portal>
                <Dialog
                    quick
                    onClose={props.onClose}
                    open
                    headline={<h1 class="m3-headline-small">{string.SELECT_GROUP_HINT()}</h1>}
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
                            {string.ENROLLMENT_GROUP_HINT({
                                break: (
                                    <>
                                        <br />
                                        <br />
                                    </>
                                ),
                            })}
                        </p>
                        <Select
                            label={string.GROUP()}
                            value={props.value ?? ''}
                            onInput={async e => {
                                const val = e.currentTarget.value
                                const parsed = val ? Number(val) : null
                                setSelected(parsed)
                            }}
                        >
                            <Option value="" hidden selected={props.value === null}>
                                {string.SELECT_GROUP_HINT()}
                            </Option>
                            <Show when={props.groups}>
                                {g =>
                                    g().map(group => (
                                        <Option value={group.id} selected={group.id === props.value}>
                                            {group.name}
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
