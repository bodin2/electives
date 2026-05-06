import { type Component, createContext, type JSXElement, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'

export interface PageData {
    title: string | Component
    leading?: string | Component
    trailing?: () => JSXElement
    setTitle: (title: string | Component) => void
    setLeading: (leading: string | Component | undefined) => void
    setTrailing: (trailing: (() => JSXElement) | undefined) => void
    topAppBarElevated: boolean
    setTopAppBarElevated: (elevated: boolean) => void
    allowBacking: boolean
    setAllowBacking: (canBack: boolean) => void
    focusable: boolean
    setFocusable: (focusable: boolean) => void
}

const PageData = createContext<PageData>(undefined as unknown as PageData)

export default function PageDataProvider(props: { children: JSXElement }) {
    const [store, setStore] = createStore<PageData>({
        title: '',
        setTitle: title => setStore('title', title),
        leading: undefined,
        setLeading: leading => setStore('leading', leading),
        trailing: undefined,
        setTrailing: trailing => setStore('trailing', trailing),
        topAppBarElevated: false,
        setTopAppBarElevated: elevated => setStore('topAppBarElevated', elevated),
        allowBacking: true,
        setAllowBacking: canBack => setStore('allowBacking', canBack),
        focusable: true,
        setFocusable: focusable => setStore('focusable', focusable),
    })

    return <PageData.Provider value={store}>{props.children}</PageData.Provider>
}

export const usePageData = () => useContext(PageData)
