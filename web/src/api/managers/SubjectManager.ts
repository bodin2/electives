import { AdminService_SubjectElectiveIds } from '@bodin2/electives-common/proto/api'
import { Subject, User } from '../structures'
import { AdminSubjectPatch, ListSubjectMembersResponse, ListSubjectsResponse, RawSubject } from '../types'
import type { Cache } from '../cache'
import type { RESTClient } from '../rest'
import type { CacheableManager, FetchOptions } from '.'

export interface SubjectMembersResult {
    teachers: User[]
    students: User[]
}

export interface SubjectFetchAllOptions extends FetchOptions {
    electiveId: number
}

export interface SubjectFetchOptions extends FetchOptions {
    electiveId: number
    subjectId: number
    /**
     * Whether to ensure the subject has a description (fetches if cached subject lacks it)
     * @default false
     */
    withDescription?: boolean
}

export interface SubjectMembersFetchOptions extends FetchOptions {
    electiveId: number
    subjectId: number
    /**
     * Whether to include students (requires authentication)
     * @default false
     */
    withStudents?: boolean
}

export interface EnrolledCountFetchOptions extends FetchOptions {
    electiveId: number
    subjectId: number
}

export class SubjectManager implements CacheableManager {
    readonly cache: Cache<number, Subject>
    // Key: "electiveId:subjectId"
    readonly enrolledCountCache: Cache<string, number>
    readonly admin: SubjectAdminActions

    private readonly electiveSubjectIds = new Map<number, Set<number>>()

    constructor(
        private readonly rest: RESTClient,
        cache: Cache<number, Subject>,
        enrolledCountCache: Cache<string, number>,
    ) {
        this.cache = cache
        this.enrolledCountCache = enrolledCountCache
        this.admin = new SubjectAdminActions(rest, this)
    }

    clearCache() {
        this.cache.clear()
        this.enrolledCountCache.clear()
        this.electiveSubjectIds.clear()
        this.admin.clearCache()
    }

    /**
     * Get the cache key for an enrolled count entry
     */
    getEnrolledCountKey(electiveId: number, subjectId: number): string {
        return `${electiveId}:${subjectId}`
    }

    /**
     * Fetch all subjects for an elective
     *
     * @param options Fetch options including electiveId
     */
    async fetchAll(options: SubjectFetchAllOptions): Promise<Subject[]> {
        const { electiveId, force = false, cache = true } = options

        if (!force) {
            const cached = this.resolveAll(electiveId)
            if (cached) return cached
        }

        const data = await this.rest.get<ListSubjectsResponse>(`/electives/${electiveId}/subjects`, {
            decoder: ListSubjectsResponse,
        })
        const subjects = data.subjects.map(s => new Subject(s))

        if (cache) {
            const subjectIds = new Set<number>()
            for (let i = 0; i < subjects.length; i++) {
                const subject = subjects[i]
                const rawSubject = data.subjects[i]
                this.cache.set(subject.id, subject)
                subjectIds.add(subject.id)
                if (rawSubject.enrolledCount !== undefined) {
                    this.enrolledCountCache.set(
                        this.getEnrolledCountKey(electiveId, subject.id),
                        rawSubject.enrolledCount,
                    )
                }
            }
            this.electiveSubjectIds.set(electiveId, subjectIds)
        }

        return subjects
    }

    /**
     * Fetch a single subject
     *
     * @param options Fetch options including electiveId and subjectId
     */
    async fetch(options: SubjectFetchOptions): Promise<Subject> {
        const { electiveId, subjectId, force = false, cache = true, withDescription = false } = options

        if (!force) {
            const cached = this.cache.get(subjectId)
            if (cached) {
                if (!withDescription || cached.description !== undefined) {
                    return cached
                }
            }
        }

        const data = await this.rest.get<RawSubject>(`/electives/${electiveId}/subjects/${subjectId}`, {
            decoder: RawSubject,
        })

        const subject = new Subject(data)

        if (cache) {
            this.cache.set(subject.id, subject)
            if (data.enrolledCount !== undefined) {
                this.enrolledCountCache.set(this.getEnrolledCountKey(electiveId, subject.id), data.enrolledCount)
            }
        }

        return subject
    }

    /**
     * Fetch enrolled count for a subject
     *
     * @param options Fetch options
     */
    async fetchEnrolledCount(options: EnrolledCountFetchOptions): Promise<number> {
        const { electiveId, subjectId, force = false, cache = true } = options
        const key = this.getEnrolledCountKey(electiveId, subjectId)

        if (!force) {
            const cached = this.enrolledCountCache.get(key)
            if (cached !== undefined) return cached
        }

        const count = await this.rest.get<number>(`/electives/${electiveId}/subjects/${subjectId}/enrolled-count`)

        if (cache) this.enrolledCountCache.set(key, count)

        return count
    }

    /**
     * Fetch members (teachers and optionally students) of a subject
     *
     * @param options Fetch options
     */
    async fetchMembers(options: SubjectMembersFetchOptions): Promise<SubjectMembersResult> {
        const { electiveId, subjectId, withStudents = false } = options

        const data = await this.rest.get<ListSubjectMembersResponse>(
            `/electives/${electiveId}/subjects/${subjectId}/members`,
            {
                query: { with_students: withStudents },
                decoder: ListSubjectMembersResponse,
            },
        )

        return {
            teachers: data.teachers.map(t => new User(t)),
            students: data.students.map(s => new User(s)),
        }
    }

    /**
     * Get a subject from cache without fetching
     *
     * @param subjectId The subject's ID
     */
    resolve(subjectId: number): Subject | undefined {
        return this.cache.get(subjectId)
    }

    /**
     * Get all subjects for an elective from cache without fetching
     *
     * @param electiveId The elective's ID
     */
    resolveAll(electiveId: number): Subject[] | undefined {
        const subjectIds = this.electiveSubjectIds.get(electiveId)
        if (!subjectIds) return undefined

        const subjects: Subject[] = []
        for (const subjectId of subjectIds) {
            const subject = this.cache.get(subjectId)
            if (subject) subjects.push(subject)
        }
        return subjects.length > 0 ? subjects : undefined
    }

    /**
     * Get enrolled count from cache without fetching
     * @param electiveId The elective's ID
     * @param subjectId The subject's ID
     */
    resolveEnrolledCount(electiveId: number, subjectId: number): number | undefined {
        return this.enrolledCountCache.get(this.getEnrolledCountKey(electiveId, subjectId))
    }

    /**
     * Update enrolled count for a subject (used by real-time updates)
     */
    _updateEnrolledCount(electiveId: number, subjectId: number, count: number): void {
        this.enrolledCountCache.set(this.getEnrolledCountKey(electiveId, subjectId), count)
    }
}

export class SubjectAdminActions {
    private cachedAll = false

    constructor(
        private readonly rest: RESTClient,
        private readonly manager: SubjectManager,
    ) {}

    clearCache(): void {
        this.cachedAll = false
    }

    /**
     * Fetch all subjects globally (partial data, no description/teachers).
     *
     * Use {@link fetch} for full data.
     * @param options Fetch options
     */
    async fetchAll(options: FetchOptions = {}): Promise<Subject[]> {
        const { force = false, cache = true } = options

        if (!force && this.cachedAll) {
            const cached = this.manager.cache.toArray()
            if (cached.length > 0) return cached
        }

        const data = await this.rest.get<ListSubjectsResponse>('/admin/subjects', {
            decoder: ListSubjectsResponse,
        })
        const subjects = data.subjects.map(s => new Subject(s))

        if (cache) {
            for (const subject of subjects) {
                this.manager.cache.set(subject.id, subject)
            }
            this.cachedAll = true
        }

        return subjects
    }

    /**
     * Fetch a single subject by ID (full data with description and teachers).
     * A full fetch replaces any partial cache entry.
     *
     * @param id The subject's ID
     * @param options Fetch options
     */
    async fetch(id: number, options: FetchOptions = {}): Promise<Subject> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.manager.cache.get(id)
            // Only return cached if it has full data (description present)
            if (cached && cached.description !== undefined) return cached
        }

        const data = await this.rest.get<RawSubject>(`/admin/subjects/${id}`, {
            decoder: RawSubject,
        })
        const subject = new Subject(data)

        if (cache) this.manager.cache.set(subject.id, subject)

        return subject
    }

    /**
     * Create or replace a subject
     *
     * @param id The subject's ID
     * @param subject The subject data
     */
    async put(id: number, subject: RawSubject): Promise<void> {
        await this.rest.put(`/admin/subjects/${id}`, subject, {
            encoder: RawSubject,
        })
        this.manager.cache.delete(id)
    }

    /**
     * Patch a subject
     *
     * @param id The subject's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminSubjectPatch): Promise<void> {
        await this.rest.patch(`/admin/subjects/${id}`, patch, {
            encoder: AdminSubjectPatch,
        })
        this.manager.cache.delete(id)
    }

    /**
     * Delete a subject
     *
     * @param id The subject's ID
     */
    async delete(id: number): Promise<void> {
        await this.rest.delete(`/admin/subjects/${id}`)
        this.manager.cache.delete(id)
    }

    /**
     * Get elective IDs that a subject belongs to
     *
     * @param id The subject's ID
     */
    async fetchElectiveIds(id: number): Promise<number[]> {
        const data = await this.rest.get<AdminService_SubjectElectiveIds>(`/admin/subjects/${id}/elective-ids`, {
            decoder: AdminService_SubjectElectiveIds,
        })
        return data.electiveIds
    }
}
