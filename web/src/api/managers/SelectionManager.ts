import { Subject } from '../structures'
import {
    AdminSetStudentSelectionsRequest,
    type RawSubject,
    SetStudentElectiveSelectionRequest,
    StudentSelections,
} from '../types'
import type { Cache } from '../cache'
import type { RESTClient } from '../rest'
import type { CacheableManager, FetchOptions } from '.'

export class SelectionManager implements CacheableManager {
    // Map<UserID, Map<ElectiveID, Subject>>
    readonly cache: Cache<number, Map<number, Subject>>
    readonly admin: SelectionAdminActions

    constructor(
        private readonly rest: RESTClient,
        cache: Cache<number, Map<number, Subject>>,
        private readonly resolveCurrentUserId: () => number,
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
        for (const [electiveIdStr, rawSubject] of Object.entries(data.subjects)) {
            const electiveId = Number.parseInt(electiveIdStr, 10)
            selections.set(electiveId, new Subject(rawSubject))
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
     * Set a selection for a student in an elective
     *
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param electiveId The elective ID
     * @param subjectId The subject ID to select
     */
    async set(userId: number | '@me', electiveId: number, subjectId: number): Promise<void> {
        const body: SetStudentElectiveSelectionRequest = { subjectId }
        await this.rest.put<void>(`/users/${userId}/selections/${electiveId}`, body, {
            encoder: SetStudentElectiveSelectionRequest,
        })

        this.cache.delete(this.resolveUserId(userId))
    }

    /**
     * Delete a selection for a student in an elective
     *
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param electiveId The elective ID
     */
    async delete(userId: number | '@me', electiveId: number): Promise<void> {
        await this.rest.delete<void>(`/users/${userId}/selections/${electiveId}`)
        this.cache.delete(this.resolveUserId(userId))
    }

    private resolveUserId(userId: number | '@me') {
        return userId === '@me' ? this.resolveCurrentUserId() : userId
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
     * @param electiveId The elective ID
     */
    resolveSelection(userId: number, electiveId: number): Subject | undefined {
        const selections = this.cache.get(userId)
        return selections?.get(electiveId)
    }
}

export class SelectionAdminActions {
    constructor(
        private readonly rest: RESTClient,
        private readonly manager: SelectionManager,
    ) {}

    /**
     * Fetch all selections for a student via admin route
     *
     * @param studentId The student's ID
     */
    async fetch(studentId: number): Promise<Map<number, Subject>> {
        const data = await this.rest.get<StudentSelections>(`/admin/users/${studentId}/selections`, {
            decoder: StudentSelections,
        })

        const selections = new Map<number, Subject>()
        for (const [electiveIdStr, rawSubject] of Object.entries(data.subjects)) {
            const electiveId = Number.parseInt(electiveIdStr, 10)
            selections.set(electiveId, new Subject(rawSubject as RawSubject))
        }

        // Cache in shared cache
        this.manager.cache.set(studentId, selections)

        return selections
    }

    /**
     * Set all selections for a student
     *
     * @param studentId The student's ID
     * @param selections Map of Elective ID -> Subject ID
     */
    async setAll(studentId: number, selections: Record<number, number>): Promise<void> {
        const body: AdminSetStudentSelectionsRequest = { selections }
        await this.rest.put(`/admin/users/${studentId}/selections`, body, {
            encoder: AdminSetStudentSelectionsRequest,
        })
        this.manager.cache.delete(studentId)
    }
}
