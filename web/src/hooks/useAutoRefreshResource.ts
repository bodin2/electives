import { type Accessor, createEffect, createResource, onCleanup } from 'solid-js'

interface UseAutoRefreshResourceOptions {
    shouldFetch: Accessor<boolean>
    getVersion: () => number | undefined
    interval?: number
}

export function useAutoRefreshResource<T>(fetcher: () => Promise<T>, options: UseAutoRefreshResourceOptions) {
    let lastFetchedVersion: number | undefined

    const [resource, { refetch }] = createResource(async () => {
        if (!options.shouldFetch()) return

        const result = await fetcher()
        lastFetchedVersion = options.getVersion()
        return result
    })

    let intervalId: ReturnType<typeof setInterval> | undefined

    const handler = () => {
        const currentVersion = options.getVersion()
        if (currentVersion !== lastFetchedVersion) {
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
