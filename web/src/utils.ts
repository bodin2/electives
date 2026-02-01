export function groupItems<T, K extends PropertyKey>(list: T[], by: (item: T) => K): Record<K, T[]> {
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

export function nonNull<T>(value: T | null | undefined, name = 'Value'): T {
    if (value == null) throw new Error(`${name} must not be nullish`)
    return value
}

export { sleep } from './api/utils'
export { electiveSorter } from './utils/electives'
export { createHashFromString, seededRandom, seededShuffle } from './utils/random'

export const noop = () => {}

export function debounce<F extends (...args: any[]) => any>(
    func: F,
    wait: number,
): (...args: Parameters<F>) => Promise<ReturnType<F>> {
    let timeout: ReturnType<typeof setTimeout> | null = null
    return function (this: any, ...args: any[]) {
        return new Promise<ReturnType<F>>(resolve => {
            if (timeout) clearTimeout(timeout)
            timeout = setTimeout(() => {
                resolve(func.apply(this, args) as ReturnType<F>)
            }, wait)
        })
    }
}
