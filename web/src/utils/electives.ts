import type { Elective } from '../api'

export function electiveSorter(a: Elective, b: Elective): number {
    if (a.endDate && b.endDate) {
        const diff = a.endDate.getTime() - b.endDate.getTime()
        if (diff !== 0) return -diff
    }

    if (a.startDate && b.startDate) {
        const diff = a.startDate.getTime() - b.startDate.getTime()
        if (diff !== 0) return diff
    }

    if (!a.startDate && b.startDate) {
        return 1
    }

    if (a.startDate && !b.startDate) {
        return -1
    }

    if (!a.endDate && b.endDate) {
        return 1
    }

    if (a.endDate && !b.endDate) {
        return -1
    }

    if (a.teamId && !b.teamId) {
        return -1
    }

    if (!a.teamId && b.teamId) {
        return 1
    }

    if (a.name < b.name) {
        return -1
    }

    if (a.name > b.name) {
        return 1
    }

    return 0
}
