import { type Accessor, createEffect, createResource, onCleanup } from 'solid-js'

interface UseAutoRefreshResourceOptions {
    shouldAutoRefetch: Accessor<boolean>
    getVersion: () => number | undefined
    interval?: number
}

export function useAutoRefreshResource<T>(fetcher: () => Promise<T>, options: UseAutoRefreshResourceOptions) {
    let lastFetchedVersion: number | undefined

    const [resource, { refetch }] = createResource(
        async () => {
            const result = await fetcher()
            lastFetchedVersion = options.getVersion()
            return result
        },
        { initialValue: undefined },
    )

    let hasInterval = false

    createEffect(() => {
        if (!options.shouldAutoRefetch() || hasInterval) return

        const intervalId = setInterval(() => {
            const currentVersion = options.getVersion()
            if (currentVersion !== lastFetchedVersion) {
                refetch()
            }
        }, options.interval ?? 5000)

        hasInterval = true

        onCleanup(() => {
            clearInterval(intervalId)
            hasInterval = false
        })
    })

    return [resource, { refetch }] as const
}
