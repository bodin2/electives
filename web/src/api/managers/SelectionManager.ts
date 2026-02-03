/**
 * Selection Manager - handles student elective selections
 */

import { nonNull } from '../../utils'
import { Subject } from '../structures'
import {
    type GetStudentSelectionsResponse,
    GetStudentSelectionsResponseCodec,
    type SetStudentElectiveSelectionRequest,
    SetStudentElectiveSelectionRequestCodec,
} from '../types'
import type { Cache } from '../cache'
import type { Client } from '../client'
import type { CacheableManager } from '.'

export interface SelectionFetchOptions {
    /** Skip cache and fetch from API */
    force?: boolean
    /**
     * Cache the result
     * @default true
     */
    cache?: boolean
}

/**
 * Manages student elective selections
 */
export class SelectionManager implements CacheableManager {
    /** Cache of student selections: `Map<UserID, Map<ElectiveID, Subject>>` */
    readonly cache: Cache<number, Map<number, Subject>>

    constructor(
        private readonly client: Client,
        cache: Cache<number, Map<number, Subject>>,
    ) {
        this.cache = cache
    }

    clearCache(): void {
        this.cache.clear()
    }

    /**
     * Fetch all selections for a user
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param options Fetch options
     */
    async fetch(userId: number | '@me', options: SelectionFetchOptions = {}): Promise<Map<number, Subject>> {
        const { force = false, cache = true } = options

        if (!force && typeof userId === 'number') {
            const cached = this.cache.get(userId)
            if (cached) return cached
        }

        const data = await this.client.rest.get<GetStudentSelectionsResponse>(`/users/${userId}/selections`, {
            decoder: GetStudentSelectionsResponseCodec,
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
     * Set a selection for a student in an elective
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param electiveId The elective ID
     * @param subjectId The subject ID to select
     */
    async set(userId: number | '@me', electiveId: number, subjectId: number): Promise<void> {
        const body: SetStudentElectiveSelectionRequest = { subjectId }
        await this.client.rest.put<void>(`/users/${userId}/selections/${electiveId}`, body, {
            encoder: SetStudentElectiveSelectionRequestCodec,
        })

        this.cache.delete(this.resolveUserId(userId))
    }

    /**
     * Delete a selection for a student in an elective
     * @param userId The user's ID, or "@me" for the authenticated user
     * @param electiveId The elective ID
     */
    async delete(userId: number | '@me', electiveId: number): Promise<void> {
        await this.client.rest.delete<void>(`/users/${userId}/selections/${electiveId}`)
        this.cache.delete(this.resolveUserId(userId))
    }

    private resolveUserId(userId: number | '@me') {
        return userId === '@me' ? nonNull(this.client.user).id : userId
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
