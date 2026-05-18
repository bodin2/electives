import { queryOptions, skipToken } from '@tanstack/solid-query'
import type { Client } from '~/api'

/**
 * @cache update-in-place
 */
export const groupsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['groups'] as const,
        queryFn: () => client.groups.fetchAll(),
    })

/**
 * @cache update-in-place
 */
export const groupQueryOptions = (client: Client<unknown>, groupId: number | typeof skipToken) =>
    queryOptions({
        queryKey: ['groups', groupId] as const,
        queryFn: groupId === skipToken ? skipToken : () => client.groups.fetch(groupId),
    })

/**
 * @cache refetch
 */
export const groupMemberCountsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['groups', 'memberCounts'] as const,
        queryFn: () => client.groups.admin.fetchMemberCounts(),
    })

/**
 * @cache remove
 */
export const groupMembersQueryOptions = (client: Client<unknown>, groupId: number, page: number, query?: string) =>
    queryOptions({
        queryKey: ['groups', groupId, 'members', { page, query }] as const,
        queryFn: () => client.groups.admin.fetchMembers(groupId, page, query),
        staleTime: query ? 5000 : 10000,
    })

/**
 * @cache remove
 */
export const groupManagersQueryOptions = (client: Client<unknown>, groupId: number, page: number, query?: string) =>
    queryOptions({
        queryKey: ['groups', groupId, 'managers', { page, query }] as const,
        queryFn: () => client.groups.admin.fetchManagers(groupId, page, query),
        staleTime: query ? 5000 : 10000,
    })
