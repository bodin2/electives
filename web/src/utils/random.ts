export function seededRandom(initialSeed: number): () => number {
    let seed = initialSeed
    return () => {
        seed = (seed + 0x6d2b79f5) | 0
        let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296
    }
}

export function seededShuffle<T>(array: T[], seed: number): T[] {
    const result = array.slice()
    const random = seededRandom(seed)
    for (let i = result.length - 1; i > 0; i--) {
        const j = Math.floor(random() * (i + 1))
        ;[result[i], result[j]] = [result[j], result[i]]
    }
    return result
}

export function createHashFromString(str: string): number {
    let hash = 0
    for (const char of str) {
        hash = (hash << 5) - hash + char.charCodeAt(0)
        hash |= 0
    }
    return hash
}
