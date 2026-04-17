import { useRouterState } from '@tanstack/solid-router'
import { createContext, createEffect, type JSXElement, onCleanup, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'

const ScrollContext = createContext({
    scrolledVertical: false,
    scrolledHorizontal: false,
    scrollX: 0,
    scrollY: 0,
    maxScrollX: 0,
    maxScrollY: 0,
})

export default function ScrollDataProvider(props: { children: JSXElement; container?: HTMLElement }) {
    const [store, setStore] = createStore(ScrollContext.defaultValue)
    const routerState = useRouterState()

    const el = () => props.container ?? document.documentElement

    const scrollListener = () => {
        const target = el()
        console.log('scroll event', {
            scrollTop: target.scrollTop,
            scrollLeft: target.scrollLeft,
        })
        setStore({
            scrolledVertical: target.scrollTop > 0,
            scrolledHorizontal: target.scrollLeft > 0,
            maxScrollX: target.scrollWidth - target.clientWidth,
            maxScrollY: target.scrollHeight - target.clientHeight,
            scrollX: target.scrollLeft,
            scrollY: target.scrollTop,
        })
    }

    createEffect(() => {
        const target = el()
        console.log('Initializing scroll listener', target)
        target.addEventListener('scroll', scrollListener, { passive: true })
        window.addEventListener('scroll', scrollListener, { passive: true })
        window.addEventListener('resize', scrollListener, { passive: true })
        onCleanup(() => {
            target.removeEventListener('scroll', scrollListener)
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
