import type { Accessor } from 'solid-js'

export function formatCountdown(timeMs: number | null): string | null {
    if (timeMs === null) return null

    const totalSeconds = Math.ceil(timeMs / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}

const cachedIntl = new Map<string, Intl.DateTimeFormat>()

export const formatDuration = (locale: Accessor<string>, date: Date) => {
    let intl = cachedIntl.get(locale())

    if (!intl) {
        intl = new Intl.DateTimeFormat(locale(), {
            dateStyle: 'medium',
            timeStyle: 'short',
        })

        cachedIntl.set(locale(), intl)
    }

    return intl.format(date)
}
