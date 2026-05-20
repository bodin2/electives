import PeopleIcon from '@iconify-icons/mdi/people-outline'
import { Icon } from 'm3-solid/src'
import { createSignal, type JSX, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { GroupSelect } from '../GroupSelect'
import { HStack, VStack } from '../Stack'
import type { IconifyIcon } from '@iconify/types'
import type { Group } from '~/api'

export interface SelectGroupDialogProps {
    open: boolean
    value: number | null
    onClose: () => unknown
    onSave: (groupId: number | null) => unknown | Promise<unknown>
    groups: Group[]
    /**
     * Optional override for the dialog headline. Defaults to {@link string.SELECT_GROUP_HINT}.
     */
    headline?: JSX.Element
    /**
     * Optional override for the descriptive paragraph rendered above the selector.
     * Defaults to the enrollment-flavored {@link string.ENROLLMENT_GROUP_HINT}.
     *
     * `null` to disable the description entirely.
     */
    description?: JSX.Element
    /**
     * Whether to show the destructive "Reset" button that calls onSave(null).
     * Defaults to true.
     */
    showReset?: boolean
    /**
     * Optional override for the save button label. Defaults to {@link string.SAVE}.
     */
    confirmLabel?: string
    /**
     * Optional override for the select component label. Defaults to {@link string.GROUP}.
     */
    selectLabel?: string
    /**
     * Optional override for the select component placeholder. Defaults to {@link string.SELECT_GROUP_HINT}.
     */
    selectPlaceholder?: string
    /**
     * Optional override for the dialog icon. Defaults to {@link PeopleIcon}.
     */
    icon?: IconifyIcon
}

export function SelectGroupDialog(props: SelectGroupDialogProps) {
    const [selected, setSelected] = createSignal(props.value)
    const { string } = useI18n()

    const showReset = () => props.showReset ?? true

    return (
        <Show when={props.open}>
            <Portal>
                <Dialog
                    quick
                    onClose={props.onClose}
                    open
                    headline={<h1 class="m3-headline-small">{props.headline ?? string.SELECT_GROUP_HINT()}</h1>}
                    icon={<Icon fill="var(--m3c-secondary)" icon={props.icon ?? PeopleIcon} />}
                    centerHeadline
                    actions={
                        <HStack as="form" method="dialog" wrap alignHorizontal="space-between">
                            <Show when={showReset()} fallback={<span />}>
                                <Button
                                    variant="tonal-error"
                                    onClick={async () => {
                                        await props.onSave(null)
                                        props.onClose()
                                    }}
                                >
                                    {string.RESET()}
                                </Button>
                            </Show>
                            <HStack style={{ 'align-self': 'flex-end' }}>
                                <Button variant="text" onClick={props.onClose}>
                                    {string.CANCEL()}
                                </Button>
                                <Button
                                    variant="text"
                                    disabled={selected() === null}
                                    onClick={async () => {
                                        await props.onSave(selected())
                                        props.onClose()
                                    }}
                                >
                                    {props.confirmLabel ?? string.SAVE()}
                                </Button>
                            </HStack>
                        </HStack>
                    }
                >
                    <VStack gap={16}>
                        <Show when={props.description !== null}>
                            <p class="text-balance text-center">
                                {props.description ??
                                    string.ENROLLMENT_GROUP_HINT({
                                        break: (
                                            <>
                                                <br />
                                                <br />
                                            </>
                                        ),
                                    })}
                            </p>
                        </Show>
                        <GroupSelect
                            required
                            label={props.selectLabel}
                            placeholder={props.selectPlaceholder}
                            value={selected()}
                            groups={props.groups}
                            onInput={setSelected}
                        />
                    </VStack>
                </Dialog>
            </Portal>
        </Show>
    )
}
