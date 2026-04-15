import { createContext, type JSXElement, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'

export interface PageData {
    title: string | (() => JSXElement)
    leading?: string | (() => JSXElement)
    trailing?: () => JSXElement
    setTitle: (title: string | (() => JSXElement)) => void
    setLeading: (leading: string | (() => JSXElement) | undefined) => void
    setTrailing: (trailing: (() => JSXElement) | undefined) => void
    topAppBarElevated: boolean
    setTopAppBarElevated: (elevated: boolean) => void
    allowBacking: boolean
    setAllowBacking: (canBack: boolean) => void
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
    })

    return <PageData.Provider value={store}>{props.children}</PageData.Provider>
}

export const usePageData = () => useContext(PageData)
