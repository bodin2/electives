import { type Accessor, createEffect, createResource, onCleanup } from 'solid-js'

interface UseAutoRefreshResourceOptions {
    shouldFetch: Accessor<boolean>
    getKey: () => string | undefined
    interval?: number
}

export function useAutoRefreshResource<T>(fetcher: () => Promise<T>, options: UseAutoRefreshResourceOptions) {
    let lastKey: string | undefined

    const [resource, { refetch }] = createResource(async () => {
        if (!options.shouldFetch()) return

        const result = await fetcher()
        lastKey = options.getKey()
        return result
    })

    let intervalId: ReturnType<typeof setInterval> | undefined

    const handler = () => {
        const currentVersion = options.getKey()
        if (currentVersion !== lastKey) {
            refetch()
        }
    }

    createEffect(() => {
        if (!options.shouldFetch() || intervalId !== undefined) return
        intervalId = setInterval(handler, options.interval ?? 5000)
    })

    // Handle initial fetch
    createEffect(() => {
        if (options.shouldFetch() && !resource.latest) {
            handler()
        }
    })

    onCleanup(() => {
        clearInterval(intervalId)
        intervalId = undefined
    })

    return [resource, { refetch }] as const
}
