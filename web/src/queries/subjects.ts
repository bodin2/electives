import { queryOptions } from '@tanstack/solid-query'
import type { Client } from '../api'
import type {
    EnrolledCountFetchOptions,
    SubjectFetchOptions,
    SubjectMembersFetchOptions,
} from '../api/managers/SubjectManager'

export const subjectQueryOptions = (client: Client<unknown>, opts: Omit<SubjectFetchOptions, 'force' | 'cache'>) =>
    queryOptions({
        queryKey: [
            'subjects',
            opts.subjectId,
            { enrollmentId: opts.enrollmentId, withDescription: opts.withDescription },
        ] as const,
        queryFn: () => client.subjects.fetch(opts),
    })

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

export const subjectEnrolledCountQueryOptions = (
    client: Client<unknown>,
    opts: Omit<EnrolledCountFetchOptions, 'force' | 'cache'>,
) =>
    queryOptions({
        queryKey: ['subjects', opts.subjectId, 'enrolledCount', opts.enrollmentId] as const,
        queryFn: () => client.subjects.fetchEnrolledCount(opts),
    })

export const adminSubjectsQueryOptions = (client: Client<unknown>) =>
    queryOptions({
        queryKey: ['admin', 'subjects'] as const,
        queryFn: () => client.subjects.admin.fetchAll(),
    })

export const adminSubjectQueryOptions = (client: Client<unknown>, subjectId: number) =>
    queryOptions({
        queryKey: ['admin', 'subjects', subjectId] as const,
        queryFn: () => client.subjects.admin.fetch(subjectId),
    })

export const adminSubjectEnrollmentIdsQueryOptions = (client: Client<unknown>, subjectId: number) =>
    queryOptions({
        queryKey: ['admin', 'subjects', subjectId, 'enrollmentIds'] as const,
        queryFn: () => client.subjects.admin.fetchEnrollmentIds(subjectId),
    })
