import { type RawEnrollment, type RawGroup, type RawSubject, type RawUser, type SubjectTag, UserType } from './types'
import type { Client } from './client'

export class Group {
    id: number
    name: string
    readonly client: Client<unknown>

    constructor(client: Client<unknown>, data: RawGroup) {
        this.client = client
        this.id = data.id
        this.name = data.name
    }

    update(data: Partial<RawGroup>) {
        if (data.id !== undefined) this.id = data.id
        if (data.name !== undefined) this.name = data.name
    }

    toJSON(): RawGroup {
        return {
            id: this.id,
            name: this.name,
        }
    }
}

export class User {
    id: number
    firstName: string
    prefix?: string
    middleName?: string
    lastName?: string
    type: UserType
    /** The user's avatar as bytes (optional) */
    avatarUrl?: string
    /** Groups the user belongs to (only for students) */
    groups: Group[]

    readonly client: Client<unknown>

    constructor(client: Client<unknown>, data: RawUser) {
        this.client = client
        this.id = data.id
        this.firstName = data.firstName
        this.prefix = data.prefix
        this.middleName = data.middleName
        this.lastName = data.lastName
        this.type = data.type
        this.avatarUrl = data.avatarUrl
        this.groups = (data.groups ?? []).map(g => client.groups._getOrCreate(g)).sort((a, b) => a.id - b.id)
    }

    /**
     * Get the user's full name
     */
    get fullName(): string {
        const names = [this.firstName, this.middleName, this.lastName].filter(Boolean)
        return names.join(' ')
    }

    /**
     * Get the user's display name, including their full name and prefixes.
     */
    get displayName(): string {
        const parts = [this.prefix, this.firstName, this.middleName, this.lastName].filter(Boolean)
        return parts.join(' ')
    }

    /**
     * Check if the user is a student
     */
    isStudent(): boolean {
        return this.type === UserType.STUDENT
    }

    /**
     * Check if the user is a teacher
     */
    isTeacher(): boolean {
        return this.type === UserType.TEACHER
    }

    /**
     * Check if the user is an admin
     */
    isAdmin(): boolean {
        return this.type === UserType.ADMIN
    }

    /**
     * Check if the user belongs to a specific group
     */
    hasGroup(groupId: number): boolean {
        return this.groups.some(g => g.id === groupId)
    }

    /**
     * Update the user with new data.
     * Only updates fields that are present in the data.
     *
     * @param data The new user data
     */
    update(data: Partial<RawUser>): void {
        if (data.firstName !== undefined) this.firstName = data.firstName
        if (data.prefix !== undefined) this.prefix = data.prefix
        if (data.middleName !== undefined) this.middleName = data.middleName
        if (data.lastName !== undefined) this.lastName = data.lastName
        if (data.type !== undefined) this.type = data.type
        if (data.avatarUrl !== undefined) this.avatarUrl = data.avatarUrl
        if (data.groups !== undefined)
            this.groups = data.groups.map(g => this.client.groups._getOrCreate(g)).sort((a, b) => a.id - b.id)
    }

    toJSON(): RawUser {
        return {
            id: this.id,
            firstName: this.firstName,
            prefix: this.prefix,
            middleName: this.middleName,
            lastName: this.lastName,
            type: this.type,
            avatarUrl: this.avatarUrl,
            groups: this.groups.map(g => g.toJSON()),
        }
    }
}

export class Enrollment {
    id: number
    name: string
    startDate?: Date
    endDate?: Date
    readonly groupId: number | null
    /** Subjects within this enrollment. `null` if not fetched. */
    subjects: Subject[] | null = null

    readonly client: Client<unknown>

    constructor(client: Client<unknown>, data: RawEnrollment) {
        this.client = client
        this.id = data.id
        this.name = data.name
        this.startDate = data.startDate ? new Date(data.startDate * 1000) : undefined
        this.endDate = data.endDate ? new Date(data.endDate * 1000) : undefined
        this.groupId = data.groupId ?? null
    }

    update(data: Partial<RawEnrollment>): void {
        if (data.name !== undefined) this.name = data.name
        if (data.startDate !== undefined) this.startDate = data.startDate ? new Date(data.startDate * 1000) : undefined
        if (data.endDate !== undefined) this.endDate = data.endDate ? new Date(data.endDate * 1000) : undefined
    }

    /**
     * Check if the selection is currently open
     */
    isSelectionOpen(): boolean {
        const now = Date.now()
        if (this.startDate && now < this.startDate.getTime()) return false
        if (this.endDate && now > this.endDate.getTime()) return false
        return true
    }

    /**
     * Check if the selection has ended
     */
    isSelectionEnded(): boolean {
        if (!this.endDate) return false
        return Date.now() > this.endDate.getTime()
    }

    /**
     * Get time until selection opens
     *
     * @returns `0` if already open or `null` if no start date
     */
    getTimeUntilOpen(): number | null {
        if (!this.startDate) return null
        const diff = this.startDate.getTime() - Date.now()
        return Math.max(0, diff)
    }

    /**
     * Get time until selection closes
     *
     * @returns `0` if already closed or `null` if no end date
     */
    getTimeUntilClose(): number | null {
        if (!this.endDate) return null
        const diff = this.endDate.getTime() - Date.now()
        return Math.max(0, diff)
    }

    toJSON(): RawEnrollment {
        return {
            id: this.id,
            name: this.name,
            startDate: this.startDate ? Math.floor(this.startDate.getTime() / 1000) : undefined,
            endDate: this.endDate ? Math.floor(this.endDate.getTime() / 1000) : undefined,
            groupId: this.groupId ?? undefined,
        }
    }
}

export class Subject {
    id: number
    name: string
    description?: string
    code: string
    tag: SubjectTag
    location: string
    capacity: number
    groupId?: number
    thumbnailUrl?: string
    imageUrl?: string

    readonly client: Client<unknown>

    constructor(client: Client<unknown>, data: RawSubject) {
        this.client = client
        this.id = data.id
        this.name = data.name
        this.description = data.description
        this.code = data.code
        this.tag = data.tag
        this.location = data.location
        this.capacity = data.capacity
        this.groupId = data.groupId
        this.thumbnailUrl = data.thumbnailUrl
        this.imageUrl = data.imageUrl
    }

    /**
     * Update the subject with new data.
     * Only updates fields that are present in the data.
     *
     * @param data The new subject data
     */
    update(data: Partial<RawSubject>): void {
        if (data.name !== undefined) this.name = data.name
        if (data.description !== undefined) this.description = data.description
        if (data.code !== undefined) this.code = data.code
        if (data.tag !== undefined) this.tag = data.tag
        if (data.location !== undefined) this.location = data.location
        if (data.capacity !== undefined) this.capacity = data.capacity
        if (data.groupId !== undefined) this.groupId = data.groupId
        if (data.thumbnailUrl !== undefined) this.thumbnailUrl = data.thumbnailUrl
        if (data.imageUrl !== undefined) this.imageUrl = data.imageUrl
    }

    canUserEnroll(user: User): boolean {
        if (!user.isStudent()) return false

        if (this.groupId != null && !user.hasGroup(this.groupId)) {
            return false
        }

        return true
    }

    /**
     * This will not include teachers, as a subject is not tied to any specific enrollment.
     */
    toJSON(): RawSubject {
        return {
            id: this.id,
            name: this.name,
            description: this.description,
            code: this.code,
            tag: this.tag,
            location: this.location,
            capacity: this.capacity,
            groupId: this.groupId,
            teachers: [],
            thumbnailUrl: this.thumbnailUrl,
            imageUrl: this.imageUrl,
        }
    }
}
