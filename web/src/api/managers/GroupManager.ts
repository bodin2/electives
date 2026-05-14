import { AdminService_GroupMemberCounts } from '@bodin2/electives-common/proto/api'
import { Group, type User } from '../structures'
import { AdminGroupPatch, AdminListGroupsResponse, AdminListUsersResponse, RawGroup } from '../types'
import type { Cache } from '../cache'
import type { Client } from '../client'
import type { RESTClient } from '../rest'
import type { CacheableManager, FetchOptions } from '.'

export class GroupManager implements CacheableManager {
    readonly cache: Cache<number, Group>
    readonly admin: GroupAdminActions

    cachedAll = false

    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        cache: Cache<number, Group>,
    ) {
        this.cache = cache
        this.admin = new GroupAdminActions(client, rest, this)
    }

    clearCache(): void {
        this.cache.clear()
        this.cachedAll = false
    }

    /**
     * Get or create a Group instance, updating it if it already exists in cache.
     */
    _getOrCreate(data: RawGroup, cache = true): Group {
        const existing = this.cache.get(data.id)
        if (existing) {
            existing.update(data)
            return existing
        }

        const group = new Group(this.client, data)
        if (cache) this.cache.set(group.id, group)
        return group
    }

    /**
     * Fetch all groups
     *
     * @param options Fetch options
     */
    async fetchAll(options: FetchOptions = {}): Promise<Group[]> {
        const { force = false, cache = true } = options

        if (!force && this.cachedAll) {
            const cached = this.cache.toArray()
            if (cached.length > 0) return cached
        }

        const data = await this.rest.get<AdminListGroupsResponse>('/admin/groups', {
            decoder: AdminListGroupsResponse,
        })
        const groups = data.groups.map(g => this._getOrCreate(g, cache))

        if (cache) {
            this.cachedAll = true
        }

        return groups
    }

    /**
     * Fetch a single group by ID
     *
     * @param id The group's ID
     * @param options Fetch options
     */
    async fetch(id: number, options: FetchOptions = {}): Promise<Group> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.cache.get(id)
            if (cached) return cached
        }

        const data = await this.rest.get<RawGroup>(`/admin/groups/${id}`, {
            decoder: RawGroup,
        })
        return this._getOrCreate(data, cache)
    }

    /**
     * Get a group from cache without fetching
     * @param id The group's ID
     */
    resolve(id: number): Group | undefined {
        return this.cache.get(id)
    }
}

export class GroupAdminActions {
    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        private readonly manager: GroupManager,
    ) {}

    /**
     * Create or replace a group
     *
     * @param id The group's ID
     * @param group The group data
     */
    async put(id: number, group: RawGroup): Promise<Group> {
        await this.rest.put(`/admin/groups/${id}`, group, {
            encoder: RawGroup,
        })
        return this.manager._getOrCreate(group)
    }

    /**
     * Patch a group
     *
     * @param id The group's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminGroupPatch): Promise<Group> {
        const data = await this.rest.patch<RawGroup>(`/admin/groups/${id}`, patch, {
            encoder: AdminGroupPatch,
            decoder: RawGroup,
        })
        return this.manager._getOrCreate(data)
    }

    /**
     * Delete a group
     *
     * @param id The group's ID
     */
    async delete(id: number): Promise<void> {
        await this.rest.delete(`/admin/groups/${id}`)
        this.manager.cache.delete(id)
    }

    /**
     * Fetch member counts for all groups
     *
     * @returns Group IDs to member counts
     */
    async fetchMemberCounts(): Promise<Record<number, number>> {
        const data = await this.rest.get<AdminService_GroupMemberCounts>('/admin/groups/member-counts', {
            decoder: AdminService_GroupMemberCounts,
        })
        return data.memberCounts
    }

    /**
     * Fetch members for a group (paginated)
     *
     * @param groupId The group's ID
     * @param page The page number (1-based)
     */
    async fetchMembers(groupId: number, page = 1, query?: string): Promise<{ users: User[]; total: number }> {
        const data = await this.rest.get<AdminListUsersResponse>(`/admin/groups/${groupId}/members`, {
            query: { page, query },
            decoder: AdminListUsersResponse,
        })
        return {
            users: data.users.map(u => this.client.users._getOrCreate(u)),
            total: data.total,
        }
    }
}
