import { type Subject, User } from '../structures'
import {
    AdminAddUserRequest,
    AdminBulkAddUsersRequest,
    AdminBulkDeleteUsersRequest,
    AdminListUsersResponse,
    AdminUserPatch,
    RawUser,
    StudentSelections,
    UnauthorizedError,
} from '../types'
import type { Cache } from '../cache'
import type { Client } from '../client'
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
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        cache: Cache<number, User>,
    ) {
        this.cache = cache
        this.admin = new UserAdminActions(client, rest, this)
    }

    clearCache(): void {
        this.cache.clear()
    }

    /**
     * Get or create a User instance, updating it if it already exists in cache.
     */
    _getOrCreate(data: RawUser, cache = true): User {
        let user = this.cache.get(data.id)
        if (user) {
            user.update(data)
        } else {
            user = new User(this.client, data)
            if (cache) this.cache.set(user.id, user)
        }
        return user
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
        return this._getOrCreate(data, cache)
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
     * Fetch subjects that a teacher is assigned to, grouped by elective
     *
     * @param userId The user's ID, or "@me" for the authenticated user
     */
    async fetchTeacherSubjects(userId: number | '@me'): Promise<Map<number, Subject>> {
        // TeacherSubjects has the same wire format as StudentSelections
        const data = await this.rest.get<StudentSelections>(`/users/${userId}/subjects`, {
            decoder: StudentSelections,
        })

        const subjects = new Map<number, Subject>()
        for (const [electiveIdStr, rawSubject] of Object.entries(data.subjects)) {
            const electiveId = Number.parseInt(electiveIdStr, 10)
            subjects.set(electiveId, this.client.subjects._getOrCreate(rawSubject))
        }

        return subjects
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
        private readonly client: Client<unknown>,
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
     * @param query The search query
     */
    async fetchStudents(page = 1, query?: string): Promise<{ users: User[]; total: number }> {
        const data = await this.rest.get<AdminListUsersResponse>('/admin/users/students', {
            query: { page, query },
            decoder: AdminListUsersResponse,
        })
        const users = data.users.map(u => this.manager._getOrCreate(u))
        return { users, total: data.total }
    }

    /**
     * Fetch teachers (paginated)
     *
     * @param page The page number (1-based)
     * @param query The search query
     */
    async fetchTeachers(page = 1, query?: string): Promise<{ users: User[]; total: number }> {
        const data = await this.rest.get<AdminListUsersResponse>('/admin/users/teachers', {
            query: { page, query },
            decoder: AdminListUsersResponse,
        })
        const users = data.users.map(u => this.manager._getOrCreate(u))
        return { users, total: data.total }
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
        return this.manager._getOrCreate(data)
    }

    /**
     * Patch a user
     *
     * @param id The user's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminUserPatch): Promise<User> {
        const data = await this.rest.patch<RawUser>(`/admin/users/${id}`, patch, {
            encoder: AdminUserPatch,
            decoder: RawUser,
        })
        return this.manager._getOrCreate(data)
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

    /**
     * Bulk add users
     *
     * @param users The users to add
     */
    async bulkAdd(users: AdminAddUserRequest[]): Promise<User[]> {
        const data = await this.rest.post<AdminListUsersResponse>(
            '/admin/users/bulk',
            { values: users },
            {
                encoder: AdminBulkAddUsersRequest,
                decoder: AdminListUsersResponse,
            },
        )
        return data.users.map(u => this.manager._getOrCreate(u))
    }

    /**
     * Bulk delete users
     *
     * @param users The users to delete
     */
    async bulkDelete(users: (User | number)[]): Promise<void> {
        const userIds = users.map(u => (typeof u === 'number' ? u : u.id))
        await this.rest.request('/admin/users/bulk', {
            method: 'DELETE',
            body: { userIds },
            encoder: AdminBulkDeleteUsersRequest,
        })
        for (const id of userIds) {
            this.manager.cache.delete(id)
        }
    }
}
