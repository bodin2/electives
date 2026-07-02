import { createSignal, onCleanup, onMount } from 'solid-js'
import type { Enrollment } from '~/api'

const MAX_TIMEOUT = 2147483647
const FIVE_MINUTES = 5 * 60 * 1000

interface UseEnrollmentOpenOptions {
    onCountdown?: (timeRemaining: number) => void
}

export default function useEnrollmentOpen(enrollment: Enrollment, options?: UseEnrollmentOpenOptions) {
    const [enrollmentOpen, setEnrollmentOpen] = createSignal(false)

    onMount(() => {
        const timeUntilOpen = enrollment.getTimeUntilOpen()
        if (timeUntilOpen === null || timeUntilOpen === 0) return setEnrollmentOpen(true)
        if (timeUntilOpen > MAX_TIMEOUT) return

        const openTimeout = setTimeout(() => {
            setEnrollmentOpen(true)
        }, timeUntilOpen)

        if (options?.onCountdown && timeUntilOpen <= FIVE_MINUTES) {
            options.onCountdown(timeUntilOpen)

            const countdownInterval = setInterval(() => {
                const remaining = enrollment.getTimeUntilOpen()
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

    onMount(() => {
        const timeUntilClose = enrollment.getTimeUntilClose()
        if (timeUntilClose === null || timeUntilClose > MAX_TIMEOUT) return
        if (timeUntilClose === 0) return setEnrollmentOpen(false)

        const closeInterval = setInterval(() => {
            setEnrollmentOpen(false)
        }, timeUntilClose)

        onCleanup(() => {
            clearInterval(closeInterval)
        })
    })

    return enrollmentOpen
}
