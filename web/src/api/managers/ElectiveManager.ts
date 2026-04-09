import { Elective, Subject } from '../structures'
import {
    AdminElectivePatch,
    AdminListElectivesResponse,
    AdminSetElectiveSubjectsRequest,
    ListElectivesResponse,
    ListSubjectsResponse,
    RawElective,
} from '../types'
import type { Cache } from '../cache'
import type { RESTClient } from '../rest'
import type { CacheableManager, FetchOptions } from '.'
import type { SubjectManager } from './SubjectManager'

export interface AdminElectiveCounts {
    selected: number
    total: number
}

export interface AdminElectiveListEntry {
    elective: Elective
    counts?: AdminElectiveCounts
}

export class ElectiveManager implements CacheableManager {
    readonly cache: Cache<number, Elective>
    readonly admin: ElectiveAdminActions
    // Whether all electives have been cached via fetchAll
    cachedAll = false

    constructor(
        private readonly rest: RESTClient,
        cache: Cache<number, Elective>,
        private readonly subjects: SubjectManager,
    ) {
        this.cache = cache
        this.admin = new ElectiveAdminActions(rest, this, subjects)
    }

    clearCache(): void {
        this.cache.clear()
        this.cachedAll = false
        this.admin.clearCache()
    }

    /**
     * Fetch all electives
     *
     * @param options Fetch options
     */
    async fetchAll(options: FetchOptions = {}): Promise<Elective[]> {
        const { force = false, cache = true } = options

        if (!force && this.cachedAll) {
            const cached = this.cache.toArray()
            if (cached.length > 0) {
                return cached
            }
        }

        const data = await this.rest.get<ListElectivesResponse>('/electives', {
            decoder: ListElectivesResponse,
        })
        const electives = data.electives.map(e => new Elective(e))

        if (cache) {
            for (const elective of electives) {
                this.cache.set(elective.id, elective)
            }

            this.cachedAll = true
        }

        return electives
    }

    /**
     * Fetch a single elective by ID
     *
     * @param id The elective's ID
     * @param options Fetch options
     */
    async fetch(id: number, options: FetchOptions = {}): Promise<Elective> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.cache.get(id)
            if (cached) return cached
        }

        const data = await this.rest.get<RawElective>(`/electives/${id}`, {
            decoder: RawElective,
        })
        const elective = new Elective(data)

        if (cache) this.cache.set(elective.id, elective)

        return elective
    }

    async fetchSubjects(electiveId: number, options: FetchOptions = {}): Promise<Subject[]> {
        return this.subjects.fetchAll({ electiveId, ...options })
    }

    /**
     * Get an elective from cache without fetching
     *
     * @param id The elective's ID
     */
    resolve(id: number): Elective | undefined {
        return this.cache.get(id)
    }

    /**
     * Resolve an elective ID from various inputs
     */
    resolveId(electiveResolvable: Elective | number): number {
        if (typeof electiveResolvable === 'number') {
            return electiveResolvable
        }
        return electiveResolvable.id
    }

    /**
     * Get all enrolled counts for an elective from cache without fetching
     *
     * @param electiveId The elective's ID
     */
    resolveAllEnrolledCounts(electiveId: number): Record<number, number> {
        const subjects = this.subjects.resolveAll(electiveId)
        if (!subjects) return {}

        const counts: Record<number, number> = {}
        for (const subject of subjects) {
            const count = this.subjects.resolveEnrolledCount(electiveId, subject.id)
            if (count !== undefined) {
                counts[subject.id] = count
            }
        }
        return counts
    }
}

export class ElectiveAdminActions {
    private cachedAll = false
    private cachedEntries: AdminElectiveListEntry[] = []

    constructor(
        private readonly rest: RESTClient,
        private readonly manager: ElectiveManager,
        private readonly subjects: SubjectManager,
    ) {}

    clearCache(): void {
        this.cachedAll = false
        this.cachedEntries = []
    }

    /**
     * Fetch all electives with enrollment counts
     *
     * @param options Fetch options
     */
    async fetchAll(options: FetchOptions = {}): Promise<AdminElectiveListEntry[]> {
        const { force = false, cache = true } = options

        if (!force && this.cachedAll && this.cachedEntries.length > 0) {
            return this.cachedEntries
        }

        const data = await this.rest.get<AdminListElectivesResponse>('/admin/electives', {
            decoder: AdminListElectivesResponse,
        })

        const entries: AdminElectiveListEntry[] = data.electives.map(e => {
            const elective = new Elective(e)
            const counts = data.selectedCounts[e.id]
            return {
                elective,
                counts: counts ? { selected: counts.selected, total: counts.total } : undefined,
            }
        })

        if (cache) {
            for (const entry of entries) {
                this.manager.cache.set(entry.elective.id, entry.elective)
            }
            this.cachedAll = true
            this.cachedEntries = entries
        }

        return entries
    }

    /**
     * Fetch a single elective by ID
     *
     * @param id The elective's ID
     * @param options Fetch options
     */
    async fetch(id: number, options: FetchOptions = {}): Promise<Elective> {
        const { force = false, cache = true } = options

        if (!force) {
            const cached = this.manager.cache.get(id)
            if (cached) return cached
        }

        const data = await this.rest.get<RawElective>(`/admin/electives/${id}`, {
            decoder: RawElective,
        })
        const elective = new Elective(data)

        if (cache) this.manager.cache.set(elective.id, elective)

        return elective
    }

    /**
     * Create or replace an elective
     *
     * @param id The elective's ID
     * @param elective The elective data
     */
    async put(id: number, elective: RawElective): Promise<void> {
        await this.rest.put(`/admin/electives/${id}`, elective, {
            encoder: RawElective,
        })
        this.manager.cache.delete(id)
    }

    /**
     * Patch an elective
     *
     * @param id The elective's ID
     * @param patch The fields to update
     */
    async patch(id: number, patch: AdminElectivePatch): Promise<void> {
        await this.rest.patch(`/admin/electives/${id}`, patch, {
            encoder: AdminElectivePatch,
        })
        this.manager.cache.delete(id)
    }

    /**
     * Delete an elective
     *
     * @param id The elective's ID
     */
    async delete(id: number): Promise<void> {
        await this.rest.delete(`/admin/electives/${id}`)
        this.manager.cache.delete(id)
    }

    /**
     * Fetch subjects for an elective via admin route
     *
     * @param electiveId The elective's ID
     */
    async fetchSubjects(electiveId: number): Promise<Subject[]> {
        const data = await this.rest.get<ListSubjectsResponse>(`/admin/electives/${electiveId}/subjects`, {
            decoder: ListSubjectsResponse,
        })
        const subjects = data.subjects.map(s => new Subject(s))

        // Also cache in shared subject cache
        for (const subject of subjects) {
            this.subjects.cache.set(subject.id, subject)
        }

        return subjects
    }

    /**
     * Set subjects for an elective
     *
     * @param electiveId The elective's ID
     * @param subjectIds The subject IDs to assign
     */
    async setSubjects(electiveId: number, subjectIds: number[]): Promise<void> {
        const body: AdminSetElectiveSubjectsRequest = { subjectIds }
        await this.rest.put(`/admin/electives/${electiveId}/subjects`, body, {
            encoder: AdminSetElectiveSubjectsRequest,
        })
    }
}
