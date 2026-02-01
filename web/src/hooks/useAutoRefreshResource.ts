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

    let hasInterval = false

    createEffect(() => {
        if (!options.shouldFetch() || hasInterval) return

        const handler = () => {
            const currentVersion = options.getVersion()
            if (currentVersion !== lastFetchedVersion) {
                refetch()
            }
        }

        const intervalId = setInterval(handler, options.interval ?? 5000)
        hasInterval = true

        handler()

        onCleanup(() => {
            clearInterval(intervalId)
            hasInterval = false
        })
    })

    return [resource, { refetch }] as const
}
