import { queryOptions } from '@tanstack/solid-query'
import type { Client } from '../api'
import type {
    EnrolledCountFetchOptions,
    SubjectFetchOptions,
    SubjectMembersFetchOptions,
} from '../api/managers/SubjectManager'

/**
 * @cache update-in-place
 */
export const subjectQueryOptions = (client: Client<unknown>, opts: Omit<SubjectFetchOptions, 'force' | 'cache'>) =>
    queryOptions({
        queryKey: [
            'subjects',
            opts.subjectId,
            { enrollmentId: opts.enrollmentId, withDescription: opts.withDescription },
        ] as const,
        queryFn: () => client.subjects.fetch(opts),
    })

/**
 * @cache remove
 */
export const subjectMembersQueryOptions = (
    client: Client<unknown>,
    opts: Omit<SubjectMembersFetchOptions, 'force' | 'cache'>,
) =>
    queryOptions({
        queryKey: [
            'subjects',
            opts.subjectId,
            'members',
            { enrollmentId: opts.enrollmentId, withStudents: opts.withStudents },
        ] as const,
        queryFn: () => client.subjects.fetchMembers(opts),
    })

/**
 * @cache update-in-place
 */
export const subjectEnrolledCountQueryOptions = (
    client: Client<unknown>,
    opts: Omit<EnrolledCountFetchOptions, 'force' | 'cache'>,
) =>
    queryOptions({
        queryKey: ['subjects', opts.subjectId, 'enrolledCount', opts.enrollmentId] as const,
        queryFn: () => client.subjects.fetchEnrolledCount(opts),
    })

/**
 * @cache update-in-place
 */
export const adminSubjectsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['admin', 'subjects'] as const,
        queryFn: () => client.subjects.admin.fetchAll(),
    })

/**
 * @cache update-in-place
 */
export const adminSubjectQueryOptions = (client: Client<unknown>, subjectId: number) =>
    queryOptions({
        queryKey: ['admin', 'subjects', subjectId] as const,
        queryFn: () => client.subjects.admin.fetch(subjectId),
    })

/**
 * @cache refetch
 */
export const adminSubjectEnrollmentIdsQueryOptions = (client: Client<unknown>, subjectId: number) =>
    queryOptions({
        queryKey: ['admin', 'subjects', subjectId, 'enrollmentIds'] as const,
        queryFn: () => client.subjects.admin.fetchEnrollmentIds(subjectId),
    })
