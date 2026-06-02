import {
    type Accessor,
    type Component,
    createContext,
    createMemo,
    getOwner,
    type JSXElement,
    onCleanup,
    useContext,
} from 'solid-js'
import { createStore, produce } from 'solid-js/store'

export interface PageOptions {
    title?: string | Component
    headerLeading?: Component
    headerTrailing?: Component
    allowBacking?: boolean
}

interface Entry {
    id: number
    get: Accessor<Partial<PageOptions>>
}

interface Registry {
    entries: Entry[]
}

const PageOptionsContext = createContext<{
    store: Registry
    add: (entry: Entry) => void
    remove: (id: number) => void
}>()

let nextId = 0

export default function PageOptionsProvider(props: { children: JSXElement }) {
    const [store, setStore] = createStore<Registry>({ entries: [] })

    const add = (entry: Entry) => {
        setStore(
            produce(s => {
                s.entries.push(entry)
            }),
        )
    }

    const remove = (id: number) => {
        setStore(
            produce(s => {
                const i = s.entries.findIndex(e => e.id === id)
                if (i >= 0) s.entries.splice(i, 1)
            }),
        )
    }

    return <PageOptionsContext.Provider value={{ store, add, remove }}>{props.children}</PageOptionsContext.Provider>
}

/**
 * Set page options for the lifetime of the caller.
 *
 * No-op when there is no surrounding {@link PageOptionsProvider}
 */
export function usePageOptions(get: Accessor<Partial<PageOptions>>) {
    const ctx = useContext(PageOptionsContext)
    if (!ctx) return
    if (!getOwner()) throw new Error('usePageOptions must be called inside a reactive owner')

    const id = ++nextId
    ctx.add({ id, get })
    onCleanup(() => ctx.remove(id))
}

/**
 * Merged options for the shell's TopAppBar. The deepest mounted component's defined value is used for each slot.
 */
export function useMergedPageOptions(): Accessor<PageOptions> {
    const ctx = useContext(PageOptionsContext)
    return createMemo(() => {
        const merged: PageOptions = {}
        if (!ctx) return merged
        for (const entry of ctx.store.entries) {
            const v = entry.get()
            for (const k in v) {
                const val = (v as Record<string, unknown>)[k]
                if (val !== undefined) (merged as Record<string, unknown>)[k] = val
            }
        }
        return merged
    })
}
