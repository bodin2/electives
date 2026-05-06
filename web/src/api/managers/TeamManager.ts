import { AdminService_TeamMemberCounts } from '@bodin2/electives-common/proto/api'
import { Team, type User } from '../structures'
import { AdminListTeamsResponse, AdminListUsersResponse, AdminTeamPatch, RawTeam } from '../types'
import type { Cache } from '../cache'
import type { Client } from '../client'
import type { RESTClient } from '../rest'
import type { CacheableManager, FetchOptions } from '.'

export class TeamManager implements CacheableManager {
    readonly cache: Cache<number, Team>
    readonly admin: TeamAdminActions

    cachedAll = false

    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        cache: Cache<number, Team>,
    ) {
        this.cache = cache
        this.admin = new TeamAdminActions(client, rest, this)
    }

    clearCache(): void {
        this.cache.clear()
        this.cachedAll = false
    }

    /**
     * Get or create a Team instance, updating it if it already exists in cache.
     */
    _getOrCreate(data: RawTeam, cache = true): Team {
        const existing = this.cache.get(data.id)
        if (existing) {
            existing.update(data)
            return existing
        }

        const team = new Team(this.client, data)
        if (cache) this.cache.set(team.id, team)
        return team
    }

    /**
     * Fetch all teams
     *
     * @param options Fetch options
     */
    async fetchAll(options: FetchOptions = {}): Promise<Team[]> {
        const { force = false, cache = true } = options

        if (!force && this.cachedAll) {
            const cached = this.cache.toArray()
            if (cached.length > 0) return cached
        }

        const data = await this.rest.get<AdminListTeamsResponse>('/admin/teams', {
            decoder: AdminListTeamsResponse,
        })
        const teams = data.teams.map(t => this._getOrCreate(t, cache))

        if (cache) {
            this.cachedAll = true
        }

        return teams
    }

    /**
     * Fetch a single team by ID
     *
     * @param id The team's ID
     * @param options Fetch options
     */
    async fetch(id: number, options: FetchOptions = {}): Promise<Team> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.cache.get(id)
            if (cached) return cached
        }

        const data = await this.rest.get<RawTeam>(`/admin/teams/${id}`, {
            decoder: RawTeam,
        })
        return this._getOrCreate(data, cache)
    }

    /**
     * Get a team from cache without fetching
     * @param id The team's ID
     */
    resolve(id: number): Team | undefined {
        return this.cache.get(id)
    }
}

export class TeamAdminActions {
    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        private readonly manager: TeamManager,
    ) {}

    /**
     * Create or replace a team
     *
     * @param id The team's ID
     * @param team The team data
     */
    async put(id: number, team: RawTeam): Promise<Team> {
        await this.rest.put(`/admin/teams/${id}`, team, {
            encoder: RawTeam,
        })
        return await this.manager.fetch(id, { force: true })
    }

    /**
     * Patch a team
     *
     * @param id The team's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminTeamPatch): Promise<Team> {
        const data = await this.rest.patch<RawTeam>(`/admin/teams/${id}`, patch, {
            encoder: AdminTeamPatch,
            decoder: RawTeam,
        })
        return this.manager._getOrCreate(data)
    }

    /**
     * Delete a team
     *
     * @param id The team's ID
     */
    async delete(id: number): Promise<void> {
        await this.rest.delete(`/admin/teams/${id}`)
        this.manager.cache.delete(id)
    }

    /**
     * Fetch member counts for all teams
     *
     * @ream IDs to member counts
     */
    async fetchMemberCounts(): Promise<Record<number, number>> {
        const data = await this.rest.get<AdminService_TeamMemberCounts>('/admin/teams/member-counts', {
            decoder: AdminService_TeamMemberCounts,
        })
        return data.memberCounts
    }

    /**
     * Fetch members for a team (paginated)
     *
     * @param teamId The team's ID
     * @param page The page number (1-based)
     */
    async fetchMembers(teamId: number, page = 1, query?: string): Promise<{ users: User[]; total: number }> {
        const data = await this.rest.get<AdminListUsersResponse>(`/admin/teams/${teamId}/members`, {
            query: { page, query },
            decoder: AdminListUsersResponse,
        })
        return {
            users: data.users.map(u => this.client.users._getOrCreate(u)),
            total: data.total,
        }
    }
}
