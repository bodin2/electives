import { Elective, Subject, User } from '../structures'
import {
    type ListElectivesResponse,
    ListElectivesResponseCodec,
    type ListSubjectMembersResponse,
    ListSubjectMembersResponseCodec,
    type ListSubjectsResponse,
    ListSubjectsResponseCodec,
    type RawElective,
    RawElectiveCodec,
    type RawSubject,
    RawSubjectCodec,
} from '../types'
import type { Cache } from '../cache'
import type { RESTClient } from '../rest'
import type { CacheableManager } from '.'
import type { FetchOptions } from './UserManager'

export interface SubjectMembersResult {
    teachers: User[]
    students: User[]
}

export class ElectiveManager implements CacheableManager {
    /** Cache of fetched electives */
    readonly cache: Cache<number, Elective>
    /** Whether all electives have been cached via {@link fetchAll} */
    cachedAll = false

    constructor(
        private readonly rest: RESTClient,
        cache: Cache<number, Elective>,
        private readonly subjectManager: SubjectManager,
    ) {
        this.cache = cache
    }

    clearCache(): void {
        this.cache.clear()
        this.cachedAll = false
    }

    /**
     * Fetch all electives
     * @param options Fetch options
     */
    async fetchAll(options: FetchOptions = {}): Promise<Elective[]> {
        const { force = false, cache = true } = options

        // If not forcing, check if we have any cached electives
        if (!force && this.cachedAll) {
            const cached = this.cache.toArray()
            if (cached.length > 0) {
                return cached
            }
        }

        const data = await this.rest.get<ListElectivesResponse>('/electives', {
            decoder: ListElectivesResponseCodec,
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
            decoder: RawElectiveCodec,
        })
        const elective = new Elective(data)

        if (cache) this.cache.set(elective.id, elective)

        return elective
    }

    async fetchSubjects(electiveId: number, options: FetchOptions = {}): Promise<Subject[]> {
        return this.subjectManager.fetchAll({ electiveId, ...options })
    }

    /**
     * Get an elective from cache without fetching
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
     * @param electiveId The elective's ID
     */
    resolveAllEnrolledCounts(electiveId: number): Record<number, number> {
        const subjects = this.subjectManager.resolveAll(electiveId)
        if (!subjects) return {}

        const counts: Record<number, number> = {}
        for (const subject of subjects) {
            const count = this.subjectManager.enrolledCountCache.get(
                this.subjectManager.getCacheKey(electiveId, subject.id),
            )
            if (count !== undefined) {
                counts[subject.id] = count
            }
        }
        return counts
    }
}

export interface SubjectFetchAllOptions extends FetchOptions {
    electiveId: number
}

export interface SubjectFetchOptions extends FetchOptions {
    electiveId: number
    subjectId: number
    /** Whether to ensure the subject has a description (fetches if cached subject lacks it) */
    withDescription?: boolean
}

export interface SubjectMembersFetchOptions extends FetchOptions {
    electiveId: number
    subjectId: number
    /** Whether to include students (requires authentication) */
    withStudents?: boolean
}

export interface EnrolledCountFetchOptions extends FetchOptions {
    electiveId: number
    subjectId: number
}

export class SubjectManager implements CacheableManager {
    /** Cache of fetched subjects (key: "electiveId:subjectId") */
    readonly cache: Cache<string, Subject>
    /** Cache of enrolled counts (key: "electiveId:subjectId") */
    readonly enrolledCountCache: Cache<string, number>
    private readonly electiveSubjectIds = new Map<number, Set<number>>()

    constructor(
        private readonly rest: RESTClient,
        cache: Cache<string, Subject>,
        enrolledCountCache: Cache<string, number>,
    ) {
        this.cache = cache
        this.enrolledCountCache = enrolledCountCache
    }

    clearCache() {
        this.cache.clear()
        this.enrolledCountCache.clear()
        this.electiveSubjectIds.clear()
    }

    getCacheKey(electiveId: number, subjectId: number): string {
        return `${electiveId}:${subjectId}`
    }

    /**
     * Fetch all subjects for an elective
     * @param options Fetch options including electiveId
     */
    async fetchAll(options: SubjectFetchAllOptions): Promise<Subject[]> {
        const { electiveId, force = false, cache = true } = options

        if (!force) {
            const cached = this.resolveAll(electiveId)
            if (cached) return cached
        }

        const data = await this.rest.get<ListSubjectsResponse>(`/electives/${electiveId}/subjects`, {
            decoder: ListSubjectsResponseCodec,
        })
        const subjects = data.subjects.map(s => new Subject(s))

        if (cache) {
            const subjectIds = new Set<number>()
            for (let i = 0; i < subjects.length; i++) {
                const subject = subjects[i]
                const rawSubject = data.subjects[i]
                const cacheKey = this.getCacheKey(electiveId, subject.id)
                this.cache.set(cacheKey, subject)
                subjectIds.add(subject.id)
                if (rawSubject.enrolledCount !== undefined) {
                    this.enrolledCountCache.set(cacheKey, rawSubject.enrolledCount)
                }
            }
            this.electiveSubjectIds.set(electiveId, subjectIds)
        }

        return subjects
    }

    /**
     * Fetch a single subject
     * @param options Fetch options including electiveId and subjectId
     */
    async fetch(options: SubjectFetchOptions): Promise<Subject> {
        const { electiveId, subjectId, force = false, cache = true, withDescription = false } = options
        const cacheKey = this.getCacheKey(electiveId, subjectId)

        if (!force) {
            const cached = this.cache.get(cacheKey)
            if (cached) {
                if (!withDescription || cached.description !== undefined) {
                    return cached
                }
            }
        }

        const data = await this.rest.get<RawSubject>(`/electives/${electiveId}/subjects/${subjectId}`, {
            decoder: RawSubjectCodec,
        })

        const subject = new Subject(data)

        if (cache) {
            this.cache.set(cacheKey, subject)
            if (data.enrolledCount !== undefined) {
                this.enrolledCountCache.set(cacheKey, data.enrolledCount)
            }
        }

        return subject
    }

    /**
     * Fetch enrolled count for a subject
     * @param options Fetch options
     */
    async fetchEnrolledCount(options: EnrolledCountFetchOptions): Promise<number> {
        const { electiveId, subjectId, force = false, cache = true } = options
        const cacheKey = this.getCacheKey(electiveId, subjectId)

        if (!force) {
            const cached = this.enrolledCountCache.get(cacheKey)
            if (cached !== undefined) return cached
        }

        const count = await this.rest.get<number>(`/electives/${electiveId}/subjects/${subjectId}/enrolled-count`)

        if (cache) this.enrolledCountCache.set(cacheKey, count)

        return count
    }

    /**
     * Fetch members (teachers and optionally students) of a subject
     * @param options Fetch options
     */
    async fetchMembers(options: SubjectMembersFetchOptions): Promise<SubjectMembersResult> {
        const { electiveId, subjectId, withStudents = false } = options

        const data = await this.rest.get<ListSubjectMembersResponse>(
            `/electives/${electiveId}/subjects/${subjectId}/members`,
            {
                query: { with_students: withStudents },
                decoder: ListSubjectMembersResponseCodec,
            },
        )

        return {
            teachers: data.teachers.map(t => new User(t)),
            students: data.students.map(s => new User(s)),
        }
    }

    /**
     * Get a subject from cache without fetching
     * @param electiveId The elective's ID
     * @param subjectId The subject's ID
     */
    resolve(electiveId: number, subjectId: number): Subject | undefined {
        return this.cache.get(this.getCacheKey(electiveId, subjectId))
    }

    /**
     * Get all subjects for an elective from cache without fetching
     * @param electiveId The elective's ID
     */
    resolveAll(electiveId: number): Subject[] | undefined {
        const subjectIds = this.electiveSubjectIds.get(electiveId)
        if (!subjectIds) return undefined

        const subjects: Subject[] = []
        for (const subjectId of subjectIds) {
            const subject = this.cache.get(this.getCacheKey(electiveId, subjectId))
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
        return this.enrolledCountCache.get(this.getCacheKey(electiveId, subjectId))
    }

    /**
     * Update enrolled count for a subject (used by real-time updates)
     */
    _updateEnrolledCount(electiveId: number, subjectId: number, count: number): void {
        this.enrolledCountCache.set(this.getCacheKey(electiveId, subjectId), count)
    }
}
