import MagnifyIcon from '@iconify-icons/mdi/magnify'
import { TextField } from 'm3-solid'
import { createSignal, For, type JSX, Show } from 'solid-js'
import { debounce } from '../utils'
import styles from './SectionedList.module.css'
import { HStack, VStack } from './Stack'

export interface SectionedListProps<T> {
    items: Record<string, T[]>
    renderSection: (section: string, items: T[], query: string) => JSX.Element
    searchLabel?: string
    onSearch?: (query: string) => void
    headerActions?: JSX.Element
    fallback?: JSX.Element
    noResultsFallback?: JSX.Element
    class?: string
    style?: JSX.CSSProperties
}

export default function SectionedList<T>(props: SectionedListProps<T>) {
    const [query, setQuery] = createSignal('')

    const updateQuery = debounce((value: string) => {
        const q = value.toLowerCase()
        setQuery(q)
        props.onSearch?.(q)
    }, 250)

    const sections = () => Object.entries(props.items).sort(([a], [b]) => a.localeCompare(b))

    return (
        <VStack gap={0} class={props.class} style={props.style}>
            <HStack alignVertical="center" gap={16} wrap class={styles.searchContainer}>
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
            <div class={styles.grid}>
                <For each={sections()} fallback={query() ? props.noResultsFallback : props.fallback}>
                    {([section, sectionItems]) => props.renderSection(section, sectionItems, query())}
                </For>
            </div>
        </VStack>
    )
}
