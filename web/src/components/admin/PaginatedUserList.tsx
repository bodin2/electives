import { ListItem, mergeClasses, TextField } from 'm3-solid'
import { type Component, createEffect, For, Show } from 'solid-js'
import { createStore } from 'solid-js/store'
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
    /** Notify the list that a user has been removed. */
    onUserRemove: (userId: number) => void
    /** Notify the list that a new user has been added. */
    onUserAdd: (user: User) => void
    /** Notify the list that a user has been edited. */
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
    /** Custom trailing component for each user item. */
    trailing?: Component<{ user: User }>
    /** Custom component that renders at the top of the list. */
    listHeader?: Component
}

const PAGE_SIZE = 50

export default function PaginatedUserList(props: PaginatedUserListProps) {
    const { string } = useI18n()

    const [store, setStore] = createStore({
        users: new Map<number, User>(),
        total: 0,
    })

    createEffect(() => {
        if (props.data) {
            setStore({
                users: props.data.users.reduce((map, user) => map.set(user.id, user), new Map<number, User>()),
                total: props.data.total,
            })
        }
    })

    const totalPages = () => {
        return Math.ceil(store.total / PAGE_SIZE)
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
            onUserRemove: userId => {
                setStore('users', u => {
                    const newUserMap = new Map(u)
                    newUserMap.delete(userId)
                    return newUserMap
                })
                setStore('total', t => Math.max(0, t - 1))
            },
            onUserAdd: user => {
                setStore('users', u => {
                    const newUserMap = new Map(u)
                    newUserMap.set(user.id, user)
                    return newUserMap
                })
            },
            onUserEdit: user => {
                setStore('users', u => {
                    const newUserMap = new Map(u)
                    newUserMap.set(user.id, user)
                    return newUserMap
                })
            },
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
                <p class="m3-label-large text-surface-variant">{string.USERS_COUNT({ count: store.total })}</p>
            </VStack>
            <VStack gap={0}>
                {props.listHeader?.({})}
                <Show when={props.isLoading && !props.data && store.users.size === 0}>
                    <ListItem headline={string.LOADING()} />
                </Show>
                <Show when={props.data || store.users.size > 0}>
                    <For
                        each={Array.from(store.users.values())}
                        fallback={
                            <Show when={!props.isLoading}>
                                <p class={mergeClasses('text-surface-variant', styles.padded)}>
                                    {string.NO_USERS_FOUND()}
                                </p>
                            </Show>
                        }
                    >
                        {user => (
                            <SubjectMemberListItem
                                showId
                                onClick={props.onClick && (() => nonNull(props.onClick)(user))}
                                user={user}
                                trailing={props.trailing}
                            />
                        )}
                    </For>
                </Show>
            </VStack>
        </>
    )
}
