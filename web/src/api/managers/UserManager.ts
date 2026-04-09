import { User } from '../structures'
import { AdminAddUserRequest, AdminListUsersResponse, AdminUserPatch, RawUser, UnauthorizedError } from '../types'
import type { Cache } from '../cache'
import type { RESTClient } from '../rest'
import type { CacheableManager } from '.'

export interface FetchOptions {
    /**
     * Whether to skip cache and fetch from API
     */
    force?: boolean
    /**
     * Whether to cache the result
     * @default true
     */
    cache?: boolean
}

export class UserManager implements CacheableManager {
    readonly cache: Cache<number, User>
    readonly admin: UserAdminActions

    constructor(
        private readonly rest: RESTClient,
        cache: Cache<number, User>,
    ) {
        this.cache = cache
        this.admin = new UserAdminActions(rest, this)
    }

    clearCache(): void {
        this.cache.clear()
    }

    /**
     * Fetch a user by ID
     *
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
            decoder: RawUser,
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
     *
     * @param options Fetch options
     */
    async fetchMe(options: FetchOptions = {}): Promise<User> {
        return this.fetch('@me', options)
    }

    /**
     * Get a user from cache without fetching
     *
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

export class UserAdminActions {
    constructor(
        private readonly rest: RESTClient,
        private readonly manager: UserManager,
    ) {}

    /**
     * Check if the current token has admin privileges by making a HEAD request to an admin-only endpoint.
     */
    async loggedIn() {
        try {
            await this.rest.request('/admin', {
                method: 'HEAD',
            })
        } catch (e) {
            if (e instanceof UnauthorizedError) return false
            throw e
        }

        return true
    }

    /**
     * Fetch students (paginated)
     * @param page The page number (1-based)
     */
    async fetchStudents(page = 1): Promise<{ users: User[]; total: number }> {
        const data = await this.rest.get<AdminListUsersResponse>('/admin/users/students', {
            query: { page },
            decoder: AdminListUsersResponse,
        })
        const users = data.users.map(u => new User(u))
        for (const user of users) {
            this.manager.cache.set(user.id, user)
        }
        return { users, total: data.total }
    }

    /**
     * Fetch teachers (paginated)
     *
     * @param page The page number (1-based)
     */
    async fetchTeachers(page = 1): Promise<{ users: User[]; total: number }> {
        const data = await this.rest.get<AdminListUsersResponse>('/admin/users/teachers', {
            query: { page },
            decoder: AdminListUsersResponse,
        })
        const users = data.users.map(u => new User(u))
        for (const user of users) {
            this.manager.cache.set(user.id, user)
        }
        return { users, total: data.total }
    }

    /**
     * Fetch a single user by ID via admin route
     *
     * @param id The user's ID
     * @param options Fetch options
     */
    async fetch(id: number, options: FetchOptions = {}): Promise<User> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.manager.cache.get(id)
            if (cached) return cached
        }

        const data = await this.rest.get<RawUser>(`/admin/users/${id}`, {
            decoder: RawUser,
        })
        const user = new User(data)

        if (cache) this.manager.cache.set(user.id, user)

        return user
    }

    /**
     * Create or replace a user
     *
     * @param id The user's ID
     * @param request The user data and password
     */
    async put(id: number, request: AdminAddUserRequest): Promise<User> {
        const data = await this.rest.put<RawUser>(`/admin/users/${id}`, request, {
            encoder: AdminAddUserRequest,
            decoder: RawUser,
        })
        const user = new User(data)
        this.manager.cache.set(user.id, user)
        return user
    }

    /**
     * Patch a user
     *
     * @param id The user's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminUserPatch): Promise<void> {
        await this.rest.patch(`/admin/users/${id}`, patch, {
            encoder: AdminUserPatch,
        })
        this.manager.cache.delete(id)
    }

    /**
     * Delete a user
     *
     * @param id The user's ID
     */
    async delete(id: number): Promise<void> {
        await this.rest.delete(`/admin/users/${id}`)
        this.manager.cache.delete(id)
    }
}
