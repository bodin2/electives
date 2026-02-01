import { createSignal, onCleanup, onMount } from 'solid-js'
import type { Elective } from '../api'

export default function useElectiveOpen(elective: Elective) {
    const [electiveOpen, setElectiveOpen] = createSignal(false)

    onMount(() => {
        const timeUntilClose = elective.getTimeUntilClose()
        // never closes
        if (timeUntilClose === null) return
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
        // null means always open, 0 means already open
        if (timeUntilOpen === null || timeUntilOpen === 0) return setElectiveOpen(true)

        const openInterval = setInterval(() => {
            setElectiveOpen(true)
        }, timeUntilOpen)

        onCleanup(() => {
            clearInterval(openInterval)
        })
    })

    return electiveOpen
}
