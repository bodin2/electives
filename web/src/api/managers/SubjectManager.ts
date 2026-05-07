import { AdminService_SubjectElectiveIds } from '@bodin2/electives-common/proto/api'
import { Subject, type User } from '../structures'
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
    // Key: "electiveId:subjectId"
    readonly teachersCache: Cache<string, User[]>
    readonly admin: SubjectAdminActions

    private readonly electiveSubjectIds = new Map<number, Set<number>>()

    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        cache: Cache<number, Subject>,
        enrolledCountCache: Cache<string, number>,
        teachersCache: Cache<string, User[]>,
    ) {
        this.cache = cache
        this.enrolledCountCache = enrolledCountCache
        this.teachersCache = teachersCache
        this.admin = new SubjectAdminActions(client, rest, this)
    }

    clearCache() {
        this.cache.clear()
        this.enrolledCountCache.clear()
        this.teachersCache.clear()
        this.electiveSubjectIds.clear()
        this.admin.clearCache()
    }

    /**
     * Clear the cached subject-to-elective mapping for a specific elective,
     * forcing the next fetch to hit the API.
     */
    clearElectiveMapping(electiveId: number): void {
        this.electiveSubjectIds.delete(electiveId)
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
        const subjects = data.subjects.map(s => this._getOrCreate(s, cache))

        if (cache) {
            const subjectIds = new Set<number>()
            for (let i = 0; i < subjects.length; i++) {
                const subject = subjects[i]
                const rawSubject = data.subjects[i]
                subjectIds.add(subject.id)
                if (rawSubject.enrolledCount !== undefined) {
                    this.enrolledCountCache.set(
                        this.getEnrolledCountKey(electiveId, subject.id),
                        rawSubject.enrolledCount,
                    )
                }
                if (rawSubject.teachers.length > 0) {
                    this.teachersCache.set(
                        this.getEnrolledCountKey(electiveId, subject.id),
                        rawSubject.teachers.map(t => this.client.users._getOrCreate(t)),
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

        const subject = this._getOrCreate(data, cache)

        if (cache) {
            if (data.enrolledCount !== undefined) {
                this.enrolledCountCache.set(this.getEnrolledCountKey(electiveId, subject.id), data.enrolledCount)
            }
            if (data.teachers.length > 0) {
                this.teachersCache.set(
                    this.getEnrolledCountKey(electiveId, subject.id),
                    data.teachers.map(t => this.client.users._getOrCreate(t)),
                )
            }
        }

        return subject
    }

    async fetchEnrolledCount(options: EnrolledCountFetchOptions): Promise<number> {
        const { electiveId, subjectId, force = false } = options
        const key = this.getEnrolledCountKey(electiveId, subjectId)

        if (!force) {
            const cached = this.enrolledCountCache.get(key)
            if (cached !== undefined) return cached
        }

        // The enrolled count is included in the subject data
        await this.fetch({ electiveId, subjectId, force })

        return this.enrolledCountCache.get(key) ?? 0
    }

    /**
     * Fetch members (teachers and optionally students) of a subject
     *
     * @param options Fetch options
     */
    async fetchMembers(options: SubjectMembersFetchOptions): Promise<SubjectMembersResult> {
        const { electiveId, subjectId, withStudents = false, cache = true } = options

        const key = `${electiveId}:${subjectId}`
        const teachersCache = this.teachersCache.get(key)

        if (!options.force && !options.withStudents && teachersCache) {
            return {
                students: [],
                teachers: teachersCache,
            }
        }

        const data = await this.rest.get<ListSubjectMembersResponse>(
            `/electives/${electiveId}/subjects/${subjectId}/members`,
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
     * Get teachers from cache without fetching
     * @param electiveId The elective's ID
     * @param subjectId The subject's ID
     */
    resolveTeachers(electiveId: number, subjectId: number): User[] | undefined {
        return this.teachersCache.get(this.getEnrolledCountKey(electiveId, subjectId))
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
        })
        return await this.fetch(id, { force: true })
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
        if (patch.patchTeachers && patch.electiveId) {
            this.manager.teachersCache.delete(this.manager.getEnrolledCountKey(patch.electiveId, id))
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
        // Clear all elective-specific caches for this subject
        for (const key of this.manager.teachersCache.keys()) {
            if (key.endsWith(`:${id}`)) this.manager.teachersCache.delete(key)
        }
        for (const key of this.manager.enrolledCountCache.keys()) {
            if (key.endsWith(`:${id}`)) this.manager.enrolledCountCache.delete(key)
        }
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
