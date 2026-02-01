import { useRouterState } from '@tanstack/solid-router'
import { createContext, createEffect, type JSXElement, onCleanup, onMount, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'

const ScrollContext = createContext({
    scrolledVertical: false,
    scrolledHorizontal: false,
    scrollX: 0,
    scrollY: 0,
    maxScrollX: 0,
    maxScrollY: 0,
})

export default function ScrollDataProvider(props: { children: JSXElement }) {
    const [store, setStore] = createStore(ScrollContext.defaultValue)
    const routerState = useRouterState()

    const scrollListener = () => {
        setStore({
            scrolledVertical: window.scrollY > 0,
            scrolledHorizontal: window.scrollX > 0,
            maxScrollX: document.documentElement.scrollWidth - window.innerWidth,
            maxScrollY: document.documentElement.scrollHeight - window.innerHeight,
            scrollX: window.scrollX,
            scrollY: window.scrollY,
        })
    }

    onMount(() => {
        window.addEventListener('scroll', scrollListener, { passive: true })
        window.addEventListener('resize', scrollListener, { passive: true })
        onCleanup(() => {
            window.removeEventListener('scroll', scrollListener)
            window.removeEventListener('resize', scrollListener)
        })
    })

    // Updates scroll position when routing has finished, sometimes onscroll doesn't trigger after routing
    createEffect(() => {
        if (routerState().isTransitioning) return
        scrollListener()
    })

    return <ScrollContext.Provider value={store}>{props.children}</ScrollContext.Provider>
}

export const useScrollData = () => useContext(ScrollContext)
