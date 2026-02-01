export interface CacheEntry<T> {
    value: T
    expiresAt: number
}

export interface CacheOptions {
    /**
     * Time-to-live in milliseconds
     * @default 300000 // 5 minutes
     */
    ttl?: number
    /**
     * Maximum number of entries to store
     * @default Number.POSITIVE_INFINITY // unlimited
     */
    maxSize?: number
}

/**
 * A Map-like cache with TTL support and LRU eviction
 */
export class Cache<K, V> {
    private readonly cache = new Map<K, CacheEntry<V>>()
    private readonly ttl: number
    private readonly maxSize: number

    constructor(options: CacheOptions = {}) {
        this.ttl = options.ttl ?? 5 * 60 * 1000 // 5 minutes default
        this.maxSize = options.maxSize ?? Number.POSITIVE_INFINITY
    }

    /**
     * Get a value from the cache
     * Returns undefined if the entry doesn't exist or has expired
     */
    get(key: K): V | undefined {
        const entry = this.cache.get(key)
        if (!entry) return undefined

        if (Date.now() > entry.expiresAt) {
            this.cache.delete(key)
            return undefined
        }

        return entry.value
    }

    /**
     * Check if a key exists and is not expired
     */
    has(key: K): boolean {
        return this.get(key) !== undefined
    }

    /**
     * Set a value in the cache
     */
    set(key: K, value: V, ttl?: number): this {
        // Evict oldest entries if at capacity
        if (this.cache.size >= this.maxSize && !this.cache.has(key)) {
            const oldestKey = this.cache.keys().next().value
            if (oldestKey !== undefined) {
                this.cache.delete(oldestKey)
            }
        }

        this.cache.set(key, {
            value,
            expiresAt: Date.now() + (ttl ?? this.ttl),
        })

        return this
    }

    /**
     * Delete a value from the cache
     */
    delete(key: K): boolean {
        return this.cache.delete(key)
    }

    /**
     * Clear all entries from the cache
     */
    clear(): void {
        this.cache.clear()
    }

    /**
     * Get the number of (possibly expired) entries in the cache
     */
    get size(): number {
        return this.cache.size
    }

    /**
     * Iterate over all non-expired values
     */
    *values(): IterableIterator<V> {
        const now = Date.now()
        for (const [key, entry] of this.cache) {
            if (now > entry.expiresAt) {
                this.cache.delete(key)
                continue
            }
            yield entry.value
        }
    }

    /**
     * Iterate over all non-expired entries
     */
    *entries(): IterableIterator<[K, V]> {
        const now = Date.now()
        for (const [key, entry] of this.cache) {
            if (now > entry.expiresAt) {
                this.cache.delete(key)
                continue
            }
            yield [key, entry.value]
        }
    }

    /**
     * Iterate over all non-expired keys
     */
    *keys(): IterableIterator<K> {
        for (const [key] of this.entries()) {
            yield key
        }
    }

    /**
     * Get all non-expired values as an array
     */
    toArray(): V[] {
        return Array.from(this.values())
    }

    /**
     * Find the first value that matches the predicate
     */
    find(predicate: (value: V, key: K) => boolean): V | undefined {
        for (const [key, value] of this.entries()) {
            if (predicate(value, key)) {
                return value
            }
        }
        return undefined
    }

    /**
     * Filter values by a predicate
     */
    filter(predicate: (value: V, key: K) => boolean): V[] {
        const result: V[] = []
        for (const [key, value] of this.entries()) {
            if (predicate(value, key)) {
                result.push(value)
            }
        }
        return result
    }

    /**
     * Map values to a new array
     */
    map<R>(fn: (value: V, key: K) => R): R[] {
        const result: R[] = []
        for (const [key, value] of this.entries()) {
            result.push(fn(value, key))
        }
        return result
    }

    /**
     * Remove expired entries from the cache
     */
    sweep(): number {
        const now = Date.now()
        let swept = 0
        for (const [key, entry] of this.cache) {
            if (now > entry.expiresAt) {
                this.cache.delete(key)
                swept++
            }
        }
        return swept
    }

    /**
     * Force refresh the TTL for an existing entry
     */
    touch(key: K, ttl?: number): boolean {
        const entry = this.cache.get(key)
        if (!entry) return false

        entry.expiresAt = Date.now() + (ttl ?? this.ttl)
        return true
    }
}
