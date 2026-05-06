import { useNavigate, useSearch } from '@tanstack/solid-router'
import { createEffect, on, onMount } from 'solid-js'

export function useTabPersistence<T extends string>(
    tab: () => T,
    setTab: (value: T) => void,
    options: {
        key?: string
        disabled?: boolean
    } = {},
) {
    const search = useSearch({ strict: false })
    const navigate = useNavigate()
    const key = options.key ?? 'tab'

    onMount(() => {
        if (options.disabled) return
        const urlTab = (search() as Record<string, unknown>)[key] as T
        if (urlTab) {
            setTab(urlTab)
        }
    })

    createEffect(
        on(
            tab,
            t => {
                if (options.disabled) return
                navigate({
                    // @ts-expect-error: Generic navigation is hard to type with TanStack Router
                    search: (prev: Record<string, unknown>) => ({ ...prev, [key]: t }),
                    replace: true,
                })
            },
            { defer: true },
        ),
    )
}
