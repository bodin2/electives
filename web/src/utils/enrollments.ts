import type { Enrollment } from '~/api'

export function enrollmentSorter(a: Enrollment, b: Enrollment): number {
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

    if (a.groupId && !b.groupId) {
        return -1
    }

    if (!a.groupId && b.groupId) {
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
