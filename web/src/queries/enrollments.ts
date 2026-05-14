import { queryOptions, skipToken } from '@tanstack/solid-query'
import type { Client, EnrollmentManager } from '~/api'

/**
 * @cache update-in-place
 */
export const enrollmentsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['enrollments'] as const,
        queryFn: () => client.enrollments.fetchAll(),
    })

/**
 * @cache update-in-place
 */
export const enrollmentQueryOptions = (client: Client<unknown>, enrollmentId: number) =>
    queryOptions({
        queryKey: ['enrollments', enrollmentId] as const,
        queryFn: () => client.enrollments.fetch(enrollmentId),
    })

/**
 * @cache refetch-cached
 */
export const enrollmentSubjectsQueryOptions = (
    client: Client<unknown>,
    enrollmentId: number,
    options?: Parameters<EnrollmentManager['fetchSubjects']>[1],
) =>
    queryOptions({
        queryKey: ['enrollments', enrollmentId, 'subjects', options] as const,
        queryFn: () => client.enrollments.fetchSubjects(enrollmentId, options),
    })

/**
 * @cache refetch
 */
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
