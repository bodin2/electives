import MagnifyIcon from '@iconify-icons/mdi/magnify'
import { mergeClasses, TextField } from 'm3-solid/src'
import { createEffect, createSignal, For, type JSX, on, onCleanup, Show } from 'solid-js'
import styles from './SectionedList.module.css'
import { HStack, VStack } from './Stack'

export interface SectionedListProps<T, TSection> {
    items: [TSection, T[]][]
    renderSection: (section: TSection, items: T[], query: string) => JSX.Element
    searchLabel?: string
    onSearch?: (query: string) => void
    headerActions?: JSX.Element
    fallback?: JSX.Element
    noResultsFallback?: JSX.Element
    searchContainerClass?: string
    class?: string
    style?: JSX.CSSProperties
}

const COLUMN_MIN_WIDTH = 448

function layoutMasonry(container: HTMLElement) {
    const children = Array.from(container.children) as HTMLElement[]
    if (children.length === 0) return

    const containerWidth = container.getBoundingClientRect().width
    const columnCount = Math.max(1, Math.floor(containerWidth / COLUMN_MIN_WIDTH))
    const columnWidth = containerWidth / columnCount
    const columnHeights = new Array(columnCount).fill(0)

    for (const child of children) {
        const shortest = columnHeights.indexOf(Math.min(...columnHeights))
        child.style.position = 'absolute'
        child.style.width = `${columnWidth}px`
        child.style.left = `${shortest * columnWidth}px`
        child.style.top = `${columnHeights[shortest]}px`
        columnHeights[shortest] += child.offsetHeight
    }

    container.style.height = `${Math.max(...columnHeights)}px`
}

export default function SectionedList<T, TSection>(props: SectionedListProps<T, TSection>) {
    const [query, setQuery] = createSignal('')
    const [gridRef, setGridRef] = createSignal<HTMLDivElement | undefined>()

    const updateQuery = (value: string) => {
        const q = value.toLowerCase()
        setQuery(q)
        props.onSearch?.(q)
    }

    const relayout = () => {
        const grid = gridRef()
        if (grid) layoutMasonry(grid)
    }

    createEffect(
        on(
            () => props.items,
            () => {
                // Wait for DOM to update after items change
                queueMicrotask(relayout)
            },
        ),
    )

    createEffect(() => {
        const grid = gridRef()
        if (!grid) return

        const resizeObserver = new ResizeObserver(relayout)
        resizeObserver.observe(grid)

        const mutationObserver = new MutationObserver(relayout)
        mutationObserver.observe(grid, { childList: true, subtree: true, attributes: true })

        onCleanup(() => {
            resizeObserver.disconnect()
            mutationObserver.disconnect()
        })
    })

    return (
        <VStack gap={0} class={props.class} style={props.style} grow>
            <HStack
                alignVertical="center"
                gap={16}
                wrap
                class={mergeClasses(styles.searchContainer, props.searchContainerClass)}
            >
                <Show when={props.searchLabel && props.onSearch}>
                    <div style={{ flex: 1 }}>
                        <TextField
                            variant="filled"
                            leadingIcon={MagnifyIcon}
                            class={styles.search}
                            label={props.searchLabel}
                            onInput={e => updateQuery(e.currentTarget.value)}
                        />
                    </div>
                </Show>
                <Show when={props.headerActions}>{props.headerActions}</Show>
            </HStack>
            <Show when={props.items.length > 0} fallback={query() ? props.noResultsFallback : props.fallback}>
                <div ref={setGridRef} class={styles.grid}>
                    <For each={props.items}>
                        {([section, sectionItems]) => props.renderSection(section, sectionItems, query())}
                    </For>
                </div>
            </Show>
        </VStack>
    )
}
