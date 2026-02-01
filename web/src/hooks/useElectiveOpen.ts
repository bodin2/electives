import { createSignal, onCleanup, onMount } from 'solid-js'
import type { Elective } from '../api'

const MAX_TIMEOUT = 2147483647
const FIVE_MINUTES = 5 * 60 * 1000

interface UseElectiveOpenOptions {
    onCountdown?: (timeRemaining: number) => void
}

export default function useElectiveOpen(elective: Elective, options?: UseElectiveOpenOptions) {
    const [electiveOpen, setElectiveOpen] = createSignal(false)

    onMount(() => {
        const timeUntilClose = elective.getTimeUntilClose()
        if (timeUntilClose === null || timeUntilClose > MAX_TIMEOUT) return
        if (timeUntilClose === 0) return setElectiveOpen(false)

        const closeInterval = setInterval(() => {
            setElectiveOpen(false)
        }, timeUntilClose)

        onCleanup(() => {
            clearInterval(closeInterval)
        })
    })

    onMount(() => {
        const timeUntilOpen = elective.getTimeUntilOpen()
        if (timeUntilOpen === null || timeUntilOpen === 0) return setElectiveOpen(true)
        if (timeUntilOpen > MAX_TIMEOUT) return

        const openTimeout = setTimeout(() => {
            setElectiveOpen(true)
        }, timeUntilOpen)

        if (options?.onCountdown && timeUntilOpen <= FIVE_MINUTES) {
            options.onCountdown(timeUntilOpen)

            const countdownInterval = setInterval(() => {
                const remaining = elective.getTimeUntilOpen()
                if (remaining !== null && remaining > 0 && remaining <= FIVE_MINUTES) {
                    options.onCountdown?.(remaining)
                } else {
                    clearInterval(countdownInterval)
                }
            }, 1000)

            onCleanup(() => clearInterval(countdownInterval))
        }

        onCleanup(() => {
            clearTimeout(openTimeout)
        })
    })

    return electiveOpen
}
