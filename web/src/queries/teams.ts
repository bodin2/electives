import { queryOptions, skipToken } from '@tanstack/solid-query'
import type { Client } from '../api'

export const teamsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['teams'] as const,
        queryFn: () => client.teams.fetchAll(),
    })

export const teamQueryOptions = (client: Client<unknown>, teamId: number | typeof skipToken) =>
    queryOptions({
        queryKey: ['teams', teamId] as const,
        queryFn: teamId === skipToken ? skipToken : () => client.teams.fetch(teamId),
    })

export const teamMemberCountsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['teams', 'memberCounts'] as const,
        queryFn: () => client.teams.admin.fetchMemberCounts(),
    })

export const teamMembersQueryOptions = (client: Client<unknown>, teamId: number, page: number, query?: string) =>
    queryOptions({
        queryKey: ['teams', teamId, 'members', { page, query }] as const,
        queryFn: () => client.teams.admin.fetchMembers(teamId, page, query),
        staleTime: query ? 5000 : 10000,
    })
