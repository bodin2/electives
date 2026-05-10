export function groupItems<T, K extends PropertyKey>(
    list: T[],
    by: (item: T) => K,
): {
    [key in K]?: T[]
} {
    return list.reduce(
        (acc, item) => {
            const key = by(item)
            // biome-ignore lint/suspicious/noAssignInExpressions: Shut up
            const list = (acc[key] ??= [])
            list.push(item)
            return acc
        },
        {} as Record<K, T[]>,
    )
}

export function groupMapItems<T, K>(list: T[], by: (item: T) => K): Map<K, T[]> {
    return list.reduce((acc, item) => {
        const key = by(item)

        let group = acc.get(key)

        if (!group) {
            group = []
            acc.set(key, group)
        }

        group.push(item)
        return acc
    }, new Map<K, T[]>())
}

export function nonNull<T>(value: T | null | undefined, msg = 'Value must not be nullish'): T {
    if (value == null) throw new Error(msg)
    return value
}

export { sleep } from './api/utils'
export { enrollmentSorter } from './utils/enrollments'
export { createHashFromString, seededRandom, seededShuffle } from './utils/random'

// biome-ignore lint/suspicious/noExplicitAny: For inferred types in debounce
export function debounce<F extends (...args: any[]) => any>(
    func: F,
    wait: number,
): (...args: Parameters<F>) => Promise<ReturnType<F>> {
    let timeout: ReturnType<typeof setTimeout> | null = null
    return function (this: unknown, ...args: Parameters<F>) {
        return new Promise<ReturnType<F>>(resolve => {
            if (timeout) clearTimeout(timeout)
            timeout = setTimeout(() => {
                resolve(func.apply(this, args) as ReturnType<F>)
            }, wait)
        })
    }
}
