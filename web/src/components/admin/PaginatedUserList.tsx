import { ListItem, mergeClasses, TextField } from 'm3-solid'
import { createEffect, For, Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { debounce, nonNull } from '../../utils'
import { Button } from '../Button'
import { HStack, VStack } from '../Stack'
import { SubjectMemberListItem } from '../subjects/SubjectMembersTab'
import styles from './PaginatedUserList.module.css'
import type { User } from '../../api'

export interface PaginatedUserListHandle {
    /** Trigger a refresh of the current page. */
    refresh: () => void
    /** Notify the list that a user has been removed. Triggers refresh. */
    onUserRemove: (userId: number) => void
    /** Notify the list that a new user has been added. Triggers refresh. */
    onUserAdd: () => void
    /** Notify the list that a user has been edited. Triggers refresh. */
    onUserEdit: (user: User) => void
}

interface PaginatedUserListProps {
    data: { users: User[]; total: number } | undefined
    page: number
    onSearch?: (query: string) => void
    searchLabel?: string
    onPageChange: (page: number) => void
    isLoading?: boolean
    onClick?: (user: User) => void
    class?: string
    ref?: (handle: PaginatedUserListHandle) => void
    onRefresh?: () => void
}

const PAGE_SIZE = 50

export default function PaginatedUserList(props: PaginatedUserListProps) {
    const { string } = useI18n()

    const totalPages = () => {
        if (!props.data) return 1
        return Math.ceil(props.data.total / PAGE_SIZE)
    }

    const next = () => {
        if (props.page < totalPages()) props.onPageChange(props.page + 1)
    }

    const prev = () => {
        if (props.page > 1) props.onPageChange(props.page - 1)
    }

    createEffect(() => {
        props.ref?.({
            refresh: () => props.onRefresh?.(),
            onUserRemove: () => props.onRefresh?.(),
            onUserAdd: () => props.onRefresh?.(),
            onUserEdit: () => props.onRefresh?.(),
        })
    })

    return (
        <>
            <VStack gap={16} class={styles.header}>
                <Show when={props.onSearch}>
                    <TextField
                        variant="filled"
                        label={props.searchLabel}
                        onInput={debounce(e => nonNull(props.onSearch)(e.target.value), 500)}
                    />
                </Show>
                <Show when={totalPages() > 1}>
                    <HStack alignVertical="center" alignHorizontal="center" gap={16}>
                        <Button size="xs" variant="text" onClick={prev} disabled={props.page === 1 || props.isLoading}>
                            {string.PREVIOUS()}
                        </Button>
                        <span class="m3-label-large text-surface-variant">
                            {string.PAGE_X_OF_Y({ x: props.page, y: totalPages() })}
                        </span>
                        <Button
                            size="xs"
                            variant="text"
                            onClick={next}
                            disabled={props.page === totalPages() || props.isLoading}
                        >
                            {string.NEXT()}
                        </Button>
                    </HStack>
                </Show>
            </VStack>
            <VStack gap={0}>
                <Show when={props.isLoading && !props.data}>
                    <ListItem headline={string.LOADING()} />
                </Show>
                <Show when={props.data}>
                    {data => (
                        <For
                            each={data().users}
                            fallback={
                                <p class={mergeClasses('text-surface-variant', styles.padded)}>
                                    {string.NO_USERS_FOUND()}
                                </p>
                            }
                        >
                            {user => <SubjectMemberListItem showId onClick={() => props.onClick?.(user)} user={user} />}
                        </For>
                    )}
                </Show>
            </VStack>
        </>
    )
}
