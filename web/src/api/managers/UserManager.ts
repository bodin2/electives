/**
 * User Manager - handles user fetching and caching
 * Similar to Discord.js UserManager
 */

import { User } from '../structures'
import { type RawUser, RawUserCodec } from '../types'
import type { Cache } from '../cache'
import type { RESTClient } from '../rest'
import type { CacheableManager } from '.'

export interface FetchOptions {
    /** Skip cache and fetch from API */
    force?: boolean
    /** Cache the result (default: true) */
    cache?: boolean
}

export class UserManager implements CacheableManager {
    /** Cache of fetched users */
    readonly cache: Cache<number, User>

    constructor(
        private readonly rest: RESTClient,
        cache: Cache<number, User>,
    ) {
        this.cache = cache
    }

    clearCache(): void {
        this.cache.clear()
    }

    /**
     * Fetch a user by ID
     * @param id The user's ID, or "@me" for the authenticated user
     * @param options Fetch options
     */
    async fetch(id: number | '@me', options: FetchOptions = {}): Promise<User> {
        const { force = false, cache = true } = options

        // Check cache first (only for numeric IDs)
        if (!force && typeof id === 'number') {
            const cached = this.cache.get(id)
            if (cached) return cached
        }

        // Fetch from API using protobuf
        const data = await this.rest.get<RawUser>(`/users/${id}`, {
            decoder: RawUserCodec,
        })
        const user = new User(data)

        // Cache the result
        if (cache) {
            this.cache.set(user.id, user)
        }

        return user
    }

    /**
     * Fetch the currently authenticated user
     * @param options Fetch options
     */
    async fetchMe(options: FetchOptions = {}): Promise<User> {
        return this.fetch('@me', options)
    }

    /**
     * Get a user from cache without fetching
     * @param id The user's ID
     */
    resolve(id: number): User | undefined {
        return this.cache.get(id)
    }

    /**
     * Resolve a user ID from various inputs
     */
    resolveId(userResolvable: User | number): number {
        if (typeof userResolvable === 'number') {
            return userResolvable
        }
        return userResolvable.id
    }
}
