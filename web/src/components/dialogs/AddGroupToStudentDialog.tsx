import AccountGroupIcon from '@iconify-icons/mdi/account-group-outline'
import { Icon } from 'm3-solid/src'
import { createRenderEffect, createSignal, For, Show } from 'solid-js'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { Option, Select } from '../Select'
import { VStack } from '../Stack'
import type { Group } from '~/api'

export default function AddGroupToStudentDialog(props: {
    open: boolean
    onClose: (picked: Group | null) => unknown
    groups: Group[]
    currentGroupIds: number[]
    headline?: string
    selectLabel?: string
    selectPlaceholder?: string
    /** When provided, a Reset button will be shown */
    onClear?: () => unknown
    initialId?: number
}) {
    const { string } = useI18n()

    const [group, setGroup] = createSignal<Group | null>(null)

    createRenderEffect(() => {
        if (!props.open) return
        const id = props.initialId
        setGroup(id != null ? (props.groups.find(g => g.id === id) ?? null) : null)
    })

    let form!: HTMLFormElement

    return (
        <Dialog
            quick
            onClose={() => props.onClose(null)}
            open={props.open}
            headline={<h1 class="m3-headline-small">{props.headline ?? string.ADD_STUDENT_TO_GROUP()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={AccountGroupIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }} ref={form}>
                    <Show when={props.onClear}>
                        <Button
                            variant="tonal-error"
                            onClick={async () => {
                                await props.onClear?.()
                                setGroup(null)
                                props.onClose(null)
                                form.submit()
                            }}
                        >
                            {string.RESET()}
                        </Button>
                    </Show>
                    <Button
                        variant="text"
                        onClick={() => {
                            setGroup(null)
                            form.submit()
                        }}
                    >
                        {string.CANCEL()}
                    </Button>
                    <Button
                        variant="text"
                        disabled={!group()}
                        onClick={() => {
                            props.onClose(group())
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
                    if (group()) {
                        props.onClose(group())
                        form.submit()
                    }
                }}
            >
                <Select
                    label={props.selectLabel ?? string.GROUPS()}
                    value={group()?.id ?? ''}
                    onInput={e => setGroup(props.groups.find(g => g.id === Number(e.currentTarget.value)) || null)}
                >
                    <Option value="" hidden selected={group() === null}>
                        {props.selectPlaceholder ?? string.SELECT_GROUP_HINT()}
                    </Option>
                    <For each={props.groups.filter(g => !props.currentGroupIds.includes(g.id))}>
                        {g => (
                            <Option value={g.id} selected={g.id === group()?.id}>
                                {g.name}
                            </Option>
                        )}
                    </For>
                </Select>
            </VStack>
        </Dialog>
    )
}
