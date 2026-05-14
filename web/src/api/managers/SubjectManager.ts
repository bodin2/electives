import { AdminService_SubjectEnrollmentIds } from '@bodin2/electives-common/proto/api'
import { type Enrollment, Subject, type User } from '../structures'
import { AdminSubjectPatch, ListSubjectMembersResponse, ListSubjectsResponse, RawSubject } from '../types'
import type { Cache } from '../cache'
import type { Client } from '../client'
import type { RESTClient } from '../rest'
import type { CacheableManager, FetchOptions } from '.'

export interface SubjectMembersResult {
    teachers: User[]
    students: User[]
}

export interface SubjectFetchAllOptions extends FetchOptions {
    enrollmentId: number
    withEnrolledCounts?: boolean
}

export interface SubjectFetchOptions extends FetchOptions {
    enrollmentId: number
    subjectId: number
    /**
     * Whether to ensure the subject has a description (fetches if cached subject lacks it)
     * @default false
     */
    withDescription?: boolean
}

export interface SubjectMembersFetchOptions extends FetchOptions {
    enrollmentId: number
    subjectId: number
    /**
     * Whether to include students (requires authentication)
     * @default false
     */
    withStudents?: boolean
}

export interface EnrolledCountFetchOptions extends FetchOptions {
    enrollmentId: number
    subjectId: number
}

export class SubjectManager implements CacheableManager {
    readonly cache: Cache<number, Subject>
    // Key: "enrollmentId:subjectId"
    readonly enrolledCountCache: Cache<string, number>
    // Key: "enrollmentId:subjectId"
    readonly teachersCache: Cache<string, User[]>
    readonly enrollmentIdsCache: Cache<Subject['id'], Enrollment['id'][]>
    readonly enrollmentSubjectIdsCache: Cache<Enrollment['id'], Subject['id'][]>

    readonly admin: SubjectAdminActions

    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        cache: Cache<number, Subject>,
        enrolledCountCache: Cache<string, number>,
        teachersCache: Cache<string, User[]>,
        enrollmentSubjectIdsCache: Cache<Enrollment['id'], Subject['id'][]>,
        enrollmentIdsCache: Cache<Subject['id'], Enrollment['id'][]>,
    ) {
        this.cache = cache
        this.enrolledCountCache = enrolledCountCache
        this.teachersCache = teachersCache
        this.enrollmentSubjectIdsCache = enrollmentSubjectIdsCache
        this.enrollmentIdsCache = enrollmentIdsCache
        this.admin = new SubjectAdminActions(client, rest, this)
    }

    clearCache() {
        this.cache.clear()
        this.enrolledCountCache.clear()
        this.teachersCache.clear()
        this.enrollmentSubjectIdsCache.clear()
        this.admin.clearCache()
    }

    /**
     * Clear the cached subject-to-enrollment mapping for a specific enrollment,
     * forcing the next fetch to hit the API.
     */
    clearEnrollmentMapping(enrollmentId: number): void {
        this.enrollmentSubjectIdsCache.delete(enrollmentId)
    }

    /**
     * Get or create a subject instance and update it with new data.
     */
    _getOrCreate(data: RawSubject, cache = true): Subject {
        let subject = this.cache.get(data.id)
        if (subject) {
            subject.update(data)
        } else {
            subject = new Subject(this.client, data)
            if (cache) this.cache.set(subject.id, subject)
        }
        return subject
    }

    /**
     * Get the cache key for a class (enrollment + subject)
     */
    getClassKey(enrollmentId: number, subjectId: number): string {
        return `${enrollmentId}:${subjectId}`
    }

    /**
     * Fetch all subjects for an enrollment
     *
     * @param options Fetch options including enrollmentId
     */
    async fetchAll(options: SubjectFetchAllOptions): Promise<Subject[]> {
        const { enrollmentId, force = false, cache = true } = options

        if (!force) {
            const cached = this.resolveAll(enrollmentId)
            if (cached) return cached
        }

        const data = await this.rest.get<ListSubjectsResponse>(`/enrollments/${enrollmentId}/subjects`, {
            decoder: ListSubjectsResponse,
        })
        const subjects = data.subjects.map(s => this._getOrCreate(s, cache))

        if (cache) {
            const subjectIds: Subject['id'][] = []

            for (const rawSubject of data.subjects) {
                subjectIds.push(rawSubject.id)

                if (rawSubject.enrolledCount !== undefined) {
                    this._updateEnrolledCount(enrollmentId, rawSubject.id, rawSubject.enrolledCount)
                }

                if (rawSubject.teachers.length > 0) {
                    this.teachersCache.set(
                        this.getClassKey(enrollmentId, rawSubject.id),
                        rawSubject.teachers.map(t => this.client.users._getOrCreate(t)),
                    )
                }
            }

            this.enrollmentSubjectIdsCache.set(enrollmentId, subjectIds)
        }

        return subjects
    }

    /**
     * Fetch a single subject
     *
     * @param options Fetch options including enrollmentId and subjectId
     */
    async fetch(options: SubjectFetchOptions): Promise<Subject> {
        const { enrollmentId, subjectId, force = false, cache = true, withDescription = false } = options

        if (!force) {
            const cached = this.cache.get(subjectId)
            if (cached) {
                if (!withDescription || cached.description !== undefined) {
                    return cached
                }
            }
        }

        const data = await this.rest.get<RawSubject>(`/enrollments/${enrollmentId}/subjects/${subjectId}`, {
            decoder: RawSubject,
        })

        const subject = this._getOrCreate(data, cache)

        if (cache) {
            if (data.enrolledCount !== undefined) {
                this.enrolledCountCache.set(this.getClassKey(enrollmentId, subject.id), data.enrolledCount)
            }
            if (data.teachers.length > 0) {
                this.teachersCache.set(
                    this.getClassKey(enrollmentId, subject.id),
                    data.teachers.map(t => this.client.users._getOrCreate(t)),
                )
            }
        }

        return subject
    }

    async fetchEnrolledCount(options: EnrolledCountFetchOptions): Promise<number> {
        const { enrollmentId, subjectId, force = false } = options
        const key = this.getClassKey(enrollmentId, subjectId)

        if (!force) {
            const cached = this.enrolledCountCache.get(key)
            if (cached !== undefined) return cached
        }

        // The enrolled count is included in the subject data
        await this.fetch({ enrollmentId, subjectId, force })

        return this.enrolledCountCache.get(key) ?? 0
    }

    /**
     * Fetch members (teachers and optionally students) of a subject
     *
     * @param options Fetch options
     */
    async fetchMembers(options: SubjectMembersFetchOptions): Promise<SubjectMembersResult> {
        const { enrollmentId, subjectId, withStudents = false, cache = true } = options

        const key = `${enrollmentId}:${subjectId}`
        const teachersCache = this.teachersCache.get(key)

        if (!options.force && !options.withStudents && teachersCache) {
            return {
                students: [],
                teachers: teachersCache,
            }
        }

        const data = await this.rest.get<ListSubjectMembersResponse>(
            `/enrollments/${enrollmentId}/subjects/${subjectId}/members`,
            {
                query: { with_students: withStudents },
                decoder: ListSubjectMembersResponse,
            },
        )

        const teachers = data.teachers.map(t => this.client.users._getOrCreate(t))
        if (cache) {
            this.teachersCache.set(key, teachers)
        }

        return {
            teachers,
            students: data.students.map(s => this.client.users._getOrCreate(s)),
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
     * Get all subjects for an enrollment from cache without fetching
     *
     * @param enrollmentId The enrollment's ID
     */
    resolveAll(enrollmentId: number): Subject[] | undefined {
        const subjectIds = this.enrollmentSubjectIdsCache.get(enrollmentId)
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
     * @param enrollmentId The enrollment's ID
     * @param subjectId The subject's ID
     */
    resolveEnrolledCount(enrollmentId: number, subjectId: number): number | undefined {
        return this.enrolledCountCache.get(this.getClassKey(enrollmentId, subjectId))
    }

    /**
     * Get teachers from cache without fetching
     * @param enrollmentId The enrollment's ID
     * @param subjectId The subject's ID
     */
    resolveTeachers(enrollmentId: number, subjectId: number): User[] | undefined {
        return this.teachersCache.get(this.getClassKey(enrollmentId, subjectId))
    }

    /**
     * Update enrolled count for a subject (used by real-time updates)
     */
    _updateEnrolledCount(enrollmentId: number, subjectId: number, count: number): void {
        this.enrolledCountCache.set(this.getClassKey(enrollmentId, subjectId), count)
    }
}

export class SubjectAdminActions {
    private cachedAll = false

    constructor(
        private readonly client: Client<unknown>,
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
        const subjects = data.subjects.map(s => this.manager._getOrCreate(s, cache))

        if (cache) {
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
        return this.manager._getOrCreate(data, cache)
    }

    /**
     * Create or replace a subject
     *
     * @param id The subject's ID
     * @param subject The subject data
     */
    async put(id: number, subject: RawSubject): Promise<Subject> {
        await this.rest.put(`/admin/subjects/${id}`, subject, {
            encoder: RawSubject,
            decoder: RawSubject,
        })
        return this.manager._getOrCreate(subject)
    }

    /**
     * Patch a subject
     *
     * @param id The subject's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminSubjectPatch): Promise<Subject> {
        const data = await this.rest.patch<RawSubject>(`/admin/subjects/${id}`, patch, {
            encoder: AdminSubjectPatch,
            decoder: RawSubject,
        })
        if (patch.patchTeachers && patch.enrollmentId) {
            this.manager.teachersCache.delete(this.manager.getClassKey(patch.enrollmentId, id))
        }
        return this.manager._getOrCreate(data)
    }

    /**
     * Delete a subject
     *
     * @param id The subject's ID
     */
    async delete(id: number): Promise<void> {
        await this.rest.delete(`/admin/subjects/${id}`)
        this.manager.cache.delete(id)
        // Clear all enrollment-specific caches for this subject
        for (const key of this.manager.teachersCache.keys()) {
            if (key.endsWith(`:${id}`)) this.manager.teachersCache.delete(key)
        }
        for (const key of this.manager.enrolledCountCache.keys()) {
            if (key.endsWith(`:${id}`)) this.manager.enrolledCountCache.delete(key)
        }
        this.manager.enrollmentIdsCache.delete(id)
    }

    /**
     * Get enrollment IDs that a subject belongs to
     *
     * @param id The subject's ID
     */
    async fetchEnrollmentIds(id: number, options: FetchOptions = {}): Promise<number[]> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.manager.enrollmentIdsCache.get(id)
            if (cached) return cached
        }

        const data = await this.rest.get<AdminService_SubjectEnrollmentIds>(`/admin/subjects/${id}/enrollment-ids`, {
            decoder: AdminService_SubjectEnrollmentIds,
        })

        if (cache) {
            this.manager.enrollmentIdsCache.set(id, data.enrollmentIds)
        }

        return data.enrollmentIds
    }
}
