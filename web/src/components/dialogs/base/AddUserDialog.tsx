import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import { createQuery, keepPreviousData } from '@tanstack/solid-query'
import { Icon } from 'm3-solid'
import { createMemo, createSignal, type JSX, Show } from 'solid-js'
import { useAPI } from '../../../providers/APIProvider'
import { useI18n } from '../../../providers/I18nProvider'
import { studentsQueryOptions, teachersQueryOptions } from '../../../queries/users'
import { debounce } from '../../../utils'
import PaginatedUserList from '../../admin/PaginatedUserList'
import { Button } from '../../Button'
import { Dialog } from '../../Dialog'
import type { User } from '../../../api'

export interface AddUserDialogProps {
    open: boolean
    onClose: () => unknown
    onSuccess?: (user: User) => unknown
    headline: string
    type: 'teacher' | 'student'
    icon?: JSX.Element
    /** IDs of users that should appear visually selected in the list. */
    selectedIds?: number[]
    /** IDs of users that should appear disabled and non-interactive in the list. */
    disabledIds?: number[]
    // biome-ignore lint/suspicious/noConfusingVoidType: No returns are fine
    onConfirm: (user: User) => Promise<void | undefined | boolean>
}

export default function AddUserDialog(props: AddUserDialogProps) {
    const api = useAPI()
    const { string } = useI18n()

    const [page, setPage] = createSignal(1)
    const [searchQuery, setSearchQuery] = createSignal<string | undefined>()
    const [error, setError] = createSignal<string | null>(null)

    const usersQuery = createQuery(() => {
        const base =
            props.type === 'teacher'
                ? teachersQueryOptions(api.client, page(), searchQuery())
                : studentsQueryOptions(api.client, page(), searchQuery())
        return {
            queryKey: base.queryKey as readonly unknown[],
            queryFn: base.queryFn as () => Promise<{ users: User[]; total: number }>,
            staleTime: base.staleTime as number | undefined,
            placeholderData: keepPreviousData,
            enabled: props.open,
            notifyOnChangeProps: ['data'],
        }
    })

    const debouncedSetSearch = createMemo(() => debounce(setSearchQuery, 350))

    return (
        <Dialog
            closedBy="any"
            onClose={props.onClose}
            open={props.open}
            onOpen={() => {
                setPage(1)
                setSearchQuery(undefined)
                setError(null)
            }}
            headline={<h1 class="m3-headline-small">{props.headline}</h1>}
            icon={props.icon || <Icon fill="var(--m3c-secondary)" icon={AddCircleIcon} />}
            centerHeadline
            actions={
                <Button variant="text" onClick={props.onClose}>
                    {string.CANCEL()}
                </Button>
            }
        >
            <Show when={error()}>{err => <p class="m3-body-medium text-error">{err()}</p>}</Show>
            <PaginatedUserList
                onSearch={debouncedSetSearch()}
                searchLabel={props.type === 'teacher' ? string.SEARCH_TEACHERS() : string.SEARCH_STUDENTS()}
                page={page()}
                data={usersQuery.data ?? { users: [], total: 0 }}
                selectedIds={props.selectedIds}
                disabledIds={props.disabledIds}
                onPageChange={setPage}
                onClick={async user => {
                    try {
                        const shouldClose = await props.onConfirm(user)
                        if (shouldClose !== false) {
                            props.onSuccess?.(api.client.users.cache.get(user.id) ?? user)
                        }
                        await props.onClose()
                    } catch (e) {
                        setError(String(e))
                    }
                }}
            />
        </Dialog>
    )
}
