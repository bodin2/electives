import { queryOptions, skipToken } from '@tanstack/solid-query'
import type { Client } from '../api'

export const electivesQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['electives'] as const,
        queryFn: () => client.electives.fetchAll(),
    })

export const electiveQueryOptions = (client: Client<unknown>, electiveId: number) =>
    queryOptions({
        queryKey: ['electives', electiveId] as const,
        queryFn: () => client.electives.fetch(electiveId),
    })

export const electiveSubjectsQueryOptions = (client: Client<unknown>, electiveId: number) =>
    queryOptions({
        queryKey: ['electives', electiveId, 'subjects'] as const,
        queryFn: () => client.electives.fetchSubjects(electiveId),
    })

export const electiveUnenrolledMembersQueryOptions = (
    client: Client<unknown>,
    electiveId: number,
    teamId: number | typeof skipToken,
    page: number,
) =>
    queryOptions({
        queryKey: ['electives', electiveId, 'unenrolledMembers', { teamId, page }] as const,
        queryFn: teamId === skipToken ? skipToken : () => client.electives.fetchUnenrolledMembers(electiveId, teamId, page),
    })
