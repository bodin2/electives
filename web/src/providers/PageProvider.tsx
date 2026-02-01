import { createContext, type JSXElement, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'

interface PageData {
    title: string | (() => JSXElement)
    trailing?: () => JSXElement
    setTitle: (title: string | (() => JSXElement)) => void
    setTrailing: (trailing: (() => JSXElement) | undefined) => void
}

const PageData = createContext<PageData>(undefined as unknown as PageData)

export default function PageDataProvider(props: { children: JSXElement }) {
    const [store, setStore] = createStore<PageData>({
        title: '',
        setTitle: title => setStore('title', title),
        trailing: undefined,
        setTrailing: trailing => setStore('trailing', trailing),
    })

    return <PageData.Provider value={store}>{props.children}</PageData.Provider>
}

export const usePageData = () => useContext(PageData)
