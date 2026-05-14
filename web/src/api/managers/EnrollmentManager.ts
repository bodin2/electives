import { Enrollment, type Subject, type User } from '../structures'
import {
    AdminEnrollmentPatch,
    AdminListUsersResponse,
    AdminSetEnrollmentSubjectsRequest,
    ListEnrollmentsResponse,
    RawEnrollment,
} from '../types'
import type { Cache } from '../cache'
import type { Client } from '../client'
import type { RESTClient } from '../rest'
import type { CacheableManager, FetchOptions } from '.'
import type { SubjectManager } from './SubjectManager'

export interface AdminEnrollmentCounts {
    selected: number
    total: number
}

export interface AdminEnrollmentListEntry {
    enrollment: Enrollment
    counts?: AdminEnrollmentCounts
}

export class EnrollmentManager implements CacheableManager {
    readonly cache: Cache<number, Enrollment>
    readonly admin: EnrollmentAdminActions
    // Whether all enrollments have been cached via fetchAll
    cachedAll = false

    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        cache: Cache<number, Enrollment>,
        private readonly subjects: SubjectManager,
    ) {
        this.cache = cache
        this.admin = new EnrollmentAdminActions(rest, this, subjects)
    }

    clearCache(): void {
        this.cache.clear()
        this.cachedAll = false
    }

    /**
     * Get or create an enrollment instance and update it with new data.
     */
    _getOrCreate(data: RawEnrollment, cache = true): Enrollment {
        let enrollment = this.cache.get(data.id)
        if (enrollment) {
            enrollment.update(data)
        } else {
            enrollment = new Enrollment(this.client, data)
            if (cache) this.cache.set(enrollment.id, enrollment)
        }
        return enrollment
    }

    /**
     * Fetch all enrollments
     *
     * @param options Fetch options
     */
    async fetchAll(options: FetchOptions = {}): Promise<Enrollment[]> {
        const { force = false, cache = true } = options

        if (!force && this.cachedAll) {
            const cached = this.cache.toArray()
            if (cached.length > 0) {
                return cached
            }
        }

        const data = await this.rest.get<ListEnrollmentsResponse>('/enrollments', {
            decoder: ListEnrollmentsResponse,
        })
        const enrollments = data.enrollments.map(e => this._getOrCreate(e, cache))

        if (cache) {
            this.cachedAll = true
        }

        return enrollments
    }

    /**
     * Fetch a single enrollment by ID
     *
     * @param id The enrollment's ID
     * @param options Fetch options
     */
    async fetch(id: number, options: FetchOptions = {}): Promise<Enrollment> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.cache.get(id)
            if (cached) return cached
        }

        const data = await this.rest.get<RawEnrollment>(`/enrollments/${id}`, {
            decoder: RawEnrollment,
        })
        return this._getOrCreate(data, cache)
    }

    async fetchSubjects(
        enrollmentId: number,
        options: FetchOptions & { withEnrolledCounts?: boolean } = {},
    ): Promise<Subject[]> {
        return this.subjects.fetchAll({ enrollmentId, withEnrolledCounts: true, ...options })
    }

    /**
     * Fetch unenrolled members of an enrollment for a specific group
     *
     * @param enrollmentId The enrollment's ID
     * @param group The group ID to filter by
     * @param page The page number (1-based)
     */
    async fetchUnenrolledMembers(
        enrollmentId: number,
        group: number,
        page = 1,
    ): Promise<{ users: User[]; total: number }> {
        const data = await this.rest.get<AdminListUsersResponse>(`/enrollments/${enrollmentId}/unenrolled-members`, {
            query: { group, page },
            decoder: AdminListUsersResponse,
        })
        const users = data.users.map(u => this.client.users._getOrCreate(u))
        return { users, total: data.total }
    }

    /**
     * Get an enrollment from cache without fetching
     *
     * @param id The enrollment's ID
     */
    resolve(id: number): Enrollment | undefined {
        return this.cache.get(id)
    }

    /**
     * Resolve an enrollment ID from various inputs
     */
    resolveId(enrollmentResolvable: Enrollment | number): number {
        if (typeof enrollmentResolvable === 'number') {
            return enrollmentResolvable
        }
        return enrollmentResolvable.id
    }

    /**
     * Get all enrolled counts for an enrollment from cache without fetching
     *
     * @param enrollmentId The enrollment's ID
     */
    resolveAllEnrolledCounts(enrollmentId: number): Record<number, number> {
        const subjects = this.subjects.resolveAll(enrollmentId)
        if (!subjects) return {}

        const counts: Record<number, number> = {}
        for (const subject of subjects) {
            const count = this.subjects.resolveEnrolledCount(enrollmentId, subject.id)
            if (count !== undefined) {
                counts[subject.id] = count
            }
        }
        return counts
    }
}

export class EnrollmentAdminActions {
    constructor(
        private readonly rest: RESTClient,
        private readonly manager: EnrollmentManager,
        private readonly subjects: SubjectManager,
    ) {}

    /**
     * Create or replace an enrollment
     *
     * @param id The enrollment's ID
     * @param enrollment The enrollment data
     */
    async put(id: number, enrollment: RawEnrollment): Promise<Enrollment> {
        await this.rest.put(`/admin/enrollments/${id}`, enrollment, {
            encoder: RawEnrollment,
        })
        return this.manager._getOrCreate(enrollment)
    }

    /**
     * Patch an enrollment
     *
     * @param id The enrollment's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminEnrollmentPatch): Promise<Enrollment> {
        const data = await this.rest.patch<RawEnrollment>(`/admin/enrollments/${id}`, patch, {
            encoder: AdminEnrollmentPatch,
            decoder: RawEnrollment,
        })
        return this.manager._getOrCreate(data)
    }

    /**
     * Delete an enrollment
     *
     * @param id The enrollment's ID
     */
    async delete(id: number): Promise<void> {
        await this.rest.delete(`/admin/enrollments/${id}`)
        this.manager.cache.delete(id)
    }

    /**
     * Set subjects for an enrollment
     *
     * @param enrollmentId The enrollment's ID
     * @param subjectIds The subject IDs to assign
     */
    async setSubjects(enrollmentId: number, subjectIds: number[]): Promise<void> {
        const body: AdminSetEnrollmentSubjectsRequest = { subjectIds }
        await this.rest.put(`/admin/enrollments/${enrollmentId}/subjects`, body, {
            encoder: AdminSetEnrollmentSubjectsRequest,
        })
        // Optimistically update caches
        this.subjects.enrollmentSubjectIdsCache.set(enrollmentId, subjectIds)
        this.subjects.clearEnrollmentMapping(enrollmentId)
        for (const id of subjectIds) {
            const prev = this.subjects.enrollmentIdsCache.get(id) ?? []
            if (!prev.includes(enrollmentId)) {
                this.subjects.enrollmentIdsCache.set(id, [...prev, enrollmentId])
            }
        }
        for (const [key, value] of this.subjects.enrollmentIdsCache.entries()) {
            if (subjectIds.includes(key)) continue
            if (value.includes(enrollmentId)) {
                this.subjects.enrollmentIdsCache.delete(key)
            }
        }
    }
}
