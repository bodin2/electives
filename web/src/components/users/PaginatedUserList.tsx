import MagnifyingIcon from '@iconify-icons/mdi/magnify'
import { ListItem, LoadingIndicator, mergeClasses, TextField } from 'm3-solid/src'
import { batch, type Component, createRenderEffect, For, type JSXElement, Show, Suspense } from 'solid-js'
import { createStore } from 'solid-js/store'
import { useI18n } from '~/providers/I18nProvider'
import { useScrollData } from '~/providers/ScrollDataProvider'
import { debounce, nonNull } from '~/utils'
import { Button } from '../Button'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { HStack, VStack } from '../Stack'
import styles from './PaginatedUserList.module.css'
import { UserListItem } from './UserListItem'
import type { User } from '~/api'

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
    onPageChange: (page: number) => void
    onPagePreload?: (page: number) => void
    onSearch?: (query: string | undefined) => void
    searchLabel?: string
    isLoading?: boolean
    onClick?: (user: User) => void
    class?: string
    ref?: (handle: PaginatedUserListHandle) => void
    showGradeGroup?: boolean
    isFetching?: boolean
    onRefresh?: () => void
    /** Custom trailing component for each user item. */
    trailing?: Component<{ user: User }>
    /** Custom component that renders at the top of the list. */
    listHeader?: Component
    /** Custom component that renders inline with the user count, e.g. filter chips. */
    filters?: Component
    /** Custom component that renders on the right side of the header. */
    headerRight?: Component
    /** IDs of users that should appear visually selected. */
    selectedIds?: number[]
    /** IDs of users that should appear disabled and non-interactive. */
    disabledIds?: number[]
    emptyElement?: JSXElement
}

const DEFAULT_PAGE_SIZE = 50

export default function PaginatedUserList(props: PaginatedUserListProps) {
    const { string } = useI18n()
    const sd = useScrollData()

    const [store, setStore] = createStore({
        users: new Map<number, User>(),
        total: 0,
    })

    createRenderEffect(() => {
        const d = props.data
        if (d) {
            setStore({
                users: d.users.reduce((map, user) => map.set(user.id, user), new Map<number, User>()),
                total: d.total,
            })
        }
    })

    const totalPages = () => Math.ceil(store.total / DEFAULT_PAGE_SIZE)

    const next = () => {
        if (props.page < totalPages()) props.onPageChange(props.page + 1)
    }

    const prev = () => {
        if (props.page > 1) props.onPageChange(props.page - 1)
    }

    const preloadNext = () => {
        if (props.page < totalPages()) props.onPagePreload?.(props.page + 1)
    }

    const preloadPrev = () => {
        if (props.page > 1) props.onPagePreload?.(props.page - 1)
    }

    createRenderEffect(() => {
        props.ref?.({
            refresh: () => props.onRefresh?.(),
            onUserRemove: userId => {
                batch(() => {
                    setStore('users', u => {
                        const newUserMap = new Map(u)
                        newUserMap.delete(userId)
                        setStore('total', newUserMap.size)
                        return newUserMap
                    })
                })
            },
            onUserAdd: user => {
                batch(() => {
                    setStore('users', u => {
                        const newUserMap = new Map(u)
                        newUserMap.set(user.id, user)
                        setStore('total', newUserMap.size)
                        return newUserMap
                    })
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
            <VStack gap={16} class={mergeClasses(styles.header, sd.scrolledVertical && styles.scrolled)}>
                <TextField
                    leadingIcon={MagnifyingIcon}
                    variant="filled"
                    label={props.searchLabel}
                    disabled={!props.onSearch}
                    onInput={debounce(e => nonNull(props.onSearch)(e.target.value.trim() || undefined), 350)}
                />
                <Show when={totalPages() > 1}>
                    <HStack alignVertical="center" alignHorizontal="center" gap={16}>
                        <Button
                            size="xs"
                            variant="text"
                            onClick={prev}
                            onMouseEnter={preloadPrev}
                            disabled={props.page === 1 || props.isLoading}
                        >
                            {string.PREVIOUS()}
                        </Button>
                        <span class="m3-label-large">{string.PAGE_X_OF_Y({ x: props.page, y: totalPages() })}</span>
                        <Button
                            size="xs"
                            variant="text"
                            onClick={next}
                            onMouseEnter={preloadNext}
                            disabled={props.page === totalPages() || props.isLoading}
                        >
                            {string.NEXT()}
                        </Button>
                    </HStack>
                </Show>
                <HStack alignHorizontal="space-between" alignVertical="center" wrap>
                    <HStack alignVertical="center" wrap>
                        <Suspense fallback={<span class="m3-label-large">{string.LOADING()}</span>}>
                            <Show when={props.data}>
                                <p class="m3-label-large">{string.USERS_COUNT({ count: store.total })}</p>
                            </Show>
                        </Suspense>
                        {props.filters?.({})}
                        <Show when={props.isFetching}>
                            <LoadingIndicator container size={24} />
                        </Show>
                    </HStack>
                    {props.headerRight?.({})}
                </HStack>
            </VStack>
            <SuspenseLoadingPage debugName="PaginatedUserListContent">
                <Show when={store.users.size > 0} fallback={props.emptyElement}>
                    <div class={mergeClasses(styles.grid, props.isFetching && styles.fetching)}>
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
                                    <UserListItem
                                        showId
                                        showGradeGroup={props.showGradeGroup}
                                        selected={props.selectedIds?.includes(user.id)}
                                        disabled={props.disabledIds?.includes(user.id)}
                                        onClick={props.onClick && (() => nonNull(props.onClick)(user))}
                                        user={user}
                                        trailing={props.trailing}
                                    />
                                )}
                            </For>
                        </Show>
                    </div>
                </Show>
            </SuspenseLoadingPage>
        </>
    )
}
