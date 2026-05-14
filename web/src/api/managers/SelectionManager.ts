import { nonNull } from '~/utils'
import { AdminSetStudentSelectionsRequest, SetStudentEnrollmentSelectionRequest, StudentSelections } from '../types'
import type { Cache } from '../cache'
import type { Client } from '../client'
import type { RESTClient } from '../rest'
import type { Subject } from '../structures'
import type { CacheableManager, FetchOptions } from '.'

export class SelectionManager implements CacheableManager {
    // Map<UserID, Map<EnrollmentID, Subject>>
    readonly cache: Cache<number, Map<number, Subject>>
    readonly admin: SelectionAdminActions

    constructor(
        private readonly client: Client<unknown>,
        private readonly rest: RESTClient,
        cache: Cache<number, Map<number, Subject>>,
    ) {
        this.cache = cache
        this.admin = new SelectionAdminActions(rest, this)
    }

    clearCache(): void {
        this.cache.clear()
    }

    /**
     * Fetch all selections for a user
     *
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param options Fetch options
     */
    async fetch(userId: number | '@me', options: FetchOptions = {}): Promise<Map<number, Subject>> {
        const { force = false, cache = true } = options

        if (!force && typeof userId === 'number') {
            const cached = this.cache.get(userId)
            if (cached) return cached
        }

        const data = await this.rest.get<StudentSelections>(`/users/${userId}/selections`, {
            decoder: StudentSelections,
        })

        const selections = new Map<number, Subject>()
        for (const [enrollmentIdStr, rawSubject] of Object.entries(data.subjects)) {
            const enrollmentId = Number.parseInt(enrollmentIdStr, 10)
            selections.set(enrollmentId, this.client.subjects._getOrCreate(rawSubject))
        }

        if (cache) {
            const resolvedUserId = this.resolveUserId(userId)
            // Cache our selections indefinitely for "@me"
            this.cache.set(resolvedUserId, selections, userId === '@me' ? Number.POSITIVE_INFINITY : undefined)
        }

        return selections
    }

    /**
     * Fetch the authenticated user's selections
     *
     * @param options Fetch options
     */
    async fetchMe(options: FetchOptions = {}): Promise<Map<number, Subject>> {
        return this.fetch('@me', options)
    }

    /**
     * Set a selection for a student in an enrollment
     *
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param enrollmentId The enrollment ID
     * @param subjectId The subject ID to select
     */
    async set(userId: number | '@me', enrollmentId: number, subjectId: number): Promise<void> {
        const body: SetStudentEnrollmentSelectionRequest = { subjectId }
        await this.rest.put<void>(`/users/${userId}/selections/${enrollmentId}`, body, {
            encoder: SetStudentEnrollmentSelectionRequest,
        })

        this.cache.delete(this.resolveUserId(userId))
    }

    /**
     * Delete a selection for a student in an enrollment
     *
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param enrollmentId The enrollment ID
     */
    async delete(userId: number | '@me', enrollmentId: number): Promise<void> {
        await this.rest.delete<void>(`/users/${userId}/selections/${enrollmentId}`)
        this.cache.delete(this.resolveUserId(userId))
    }

    private resolveUserId(userId: number | '@me') {
        return userId === '@me' ? nonNull(this.client.user, 'Must be logged in to do this action').id : userId
    }

    /**
     * Get selections from cache without fetching
     * @param userId The user's ID
     */
    resolve(userId: number): Map<number, Subject> | undefined {
        return this.cache.get(userId)
    }

    /**
     * Get a specific selection from cache without fetching
     * @param userId The user's ID
     * @param enrollmentId The enrollment ID
     */
    resolveSelection(userId: number, enrollmentId: number): Subject | undefined {
        const selections = this.cache.get(userId)
        return selections?.get(enrollmentId)
    }
}

export class SelectionAdminActions {
    constructor(
        private readonly rest: RESTClient,
        private readonly manager: SelectionManager,
    ) {}

    /**
     * Set all selections for a student
     *
     * @param studentId The student's ID
     * @param selections Map of Enrollment ID -> Subject ID
     */
    async setAll(studentId: number, selections: Record<number, number>): Promise<void> {
        const body: AdminSetStudentSelectionsRequest = { selections }
        await this.rest.put(`/admin/users/${studentId}/selections`, body, {
            encoder: AdminSetStudentSelectionsRequest,
        })
        this.manager.cache.delete(studentId)
    }
}
