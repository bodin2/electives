import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { Icon, TextField } from 'm3-solid'
import { createEffect, createSignal, type JSX, on, Show } from 'solid-js'
import { useAPI } from '../../../providers/APIProvider'
import { useI18n } from '../../../providers/I18nProvider'
import { debounce } from '../../../utils'
import { Button } from '../../Button'
import { Dialog } from '../../Dialog'
import { VStack } from '../../Stack'
import { UserListItem } from '../../users/UserListItem'
import type { User } from '../../../api'

export interface AddUserDialogProps {
    open: boolean
    onClose: () => unknown
    onSuccess?: (user: User) => unknown
    headline: string
    actionLabel: string
    idLabel: string
    icon?: JSX.Element
    validateUser: (user: User) => string | null
    // biome-ignore lint/suspicious/noConfusingVoidType: No returns are fine
    onConfirm: (user: User) => Promise<void | undefined | boolean>
}

export default function AddUserDialog(props: AddUserDialogProps) {
    const api = useAPI()
    const { string } = useI18n()

    const [idInput, setIdInput] = createSignal('')
    const [user, setUser] = createSignal<User | null>(null)
    const [error, setError] = createSignal<string | null>(null)

    const debouncedSetSearch = createMemo(() => debounce(setSearchQuery, 350))

    return (
        <Dialog
            closedBy="any"
            onClose={props.onClose}
            open={props.open}
            onOpen={() => {
                setIdInput('')
                setUser(null)
                setError(null)
            }}
            headline={<h1 class="m3-headline-small">{props.headline}</h1>}
            icon={props.icon || <Icon fill="var(--m3c-secondary)" icon={AddCircleIcon} />}
            centerHeadline
            actions={
                <>
                    <Button variant="text" onClick={props.onClose}>
                        {string.CANCEL()}
                    </Button>
                    <Button
                        disabled={error() !== null || user() === null}
                        ref={btn}
                        variant="text"
                        onClick={async () => {
                            await updateFoundUser(idInput())

                            const u = user()
                            if (!u) return

                            try {
                                const shouldClose = await props.onConfirm(u)
                                if (shouldClose !== false) {
                                    props.onSuccess?.(api.client.users.cache.get(u.id) ?? u)
                                }

                                await props.onClose()
                            } catch (e) {
                                setError(String(e))
                            }
                        }}
                    >
                        {props.actionLabel}
                    </Button>
                </>
            }
        >
            <VStack
                as="form"
                onSubmit={e => {
                    e.preventDefault()
                    btn.click()
                }}
            >
                <TextField
                    errorIcon
                    error={Boolean(error())}
                    supportingText={error()}
                    label={props.idLabel}
                    required
                    onInput={e => setIdInput(e.currentTarget.value)}
                />
                <Show when={user()}>{u => <UserListItem user={u()} />}</Show>
            </VStack>
        </Dialog>
    )
}
