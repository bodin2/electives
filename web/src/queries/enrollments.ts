import { queryOptions, skipToken } from '@tanstack/solid-query'
import type { Client } from '../api'

export const enrollmentsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['enrollments'] as const,
        queryFn: () => client.enrollments.fetchAll(),
    })

export const enrollmentQueryOptions = (client: Client<unknown>, enrollmentId: number) =>
    queryOptions({
        queryKey: ['enrollments', enrollmentId] as const,
        queryFn: () => client.enrollments.fetch(enrollmentId),
    })

export const enrollmentSubjectsQueryOptions = (client: Client<unknown>, enrollmentId: number) =>
    queryOptions({
        queryKey: ['enrollments', enrollmentId, 'subjects'] as const,
        queryFn: () => client.enrollments.fetchSubjects(enrollmentId),
    })

export const enrollmentUnenrolledMembersQueryOptions = (
    client: Client<unknown>,
    enrollmentId: number,
    groupId: number | typeof skipToken,
    page: number,
) =>
    queryOptions({
        queryKey: ['enrollments', enrollmentId, 'unenrolledMembers', { groupId, page }] as const,
        queryFn:
            groupId === skipToken
                ? skipToken
                : () => client.enrollments.fetchUnenrolledMembers(enrollmentId, groupId, page),
    })
