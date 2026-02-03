import { createEffect, onCleanup } from 'solid-js'

export function useRetryableSubscription(
    subscribe: () => void,
    unsubscribe: () => void,
    maxRetries = 5,
    retryDelay = 5000,
) {
    let cleanedUp = false

    createEffect(() => {
        const attemptSubscribe = (attempts = 0) => {
            try {
                subscribe()
            } catch (e) {
                if (attempts >= maxRetries) {
                    console.error('Failed to subscribe after multiple attempts:', e)
                    throw new Error('Failed to subscribe after multiple attempts', { cause: e })
                }

                setTimeout(() => {
                    if (cleanedUp) return
                    attemptSubscribe(attempts + 1)
                }, retryDelay)
            }
        }

        attemptSubscribe()
    })

    onCleanup(() => {
        cleanedUp = true
        unsubscribe()
    })
}
