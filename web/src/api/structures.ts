/**
 * Entity classes representing API objects
 * Similar to Discord.js structures (User, Guild, Channel, etc.)
 */

import type { RawElective, RawSubject, RawTeam, RawUser, SubjectTag, UserType } from './types'

/**
 * Represents a team/class group
 */
export class Team {
    /** The team's unique ID */
    readonly id: number
    /** The team's name */
    readonly name: string

    constructor(data: RawTeam) {
        this.id = data.id
        this.name = data.name
    }

    toJSON(): RawTeam {
        return {
            id: this.id,
            name: this.name,
        }
    }
}

/**
 * Represents a user (student or teacher)
 */
export class User {
    /** The user's unique ID */
    readonly id: number
    /** The user's first name */
    readonly firstName: string
    /** The user's middle name (optional) */
    readonly middleName?: string
    /** The user's last name */
    readonly lastName: string
    /** The user's type (STUDENT or TEACHER) */
    readonly type: UserType
    /** The user's avatar as bytes (optional) */
    readonly avatarUrl?: string
    /** Teams the user belongs to (only for students) */
    readonly teams: Team[]

    constructor(data: RawUser) {
        this.id = data.id
        this.firstName = data.firstName
        this.middleName = data.middleName
        this.lastName = data.lastName
        this.type = data.type
        this.avatarUrl = data.avatarUrl
        this.teams = (data.teams ?? []).map(t => new Team(t))
    }

    /**
     * Get the user's full name
     */
    get fullName(): string {
        if (this.middleName) {
            return `${this.firstName} ${this.middleName} ${this.lastName}`
        }
        return `${this.firstName} ${this.lastName}`
    }

    /**
     * Check if the user is a student
     */
    isStudent(): boolean {
        return this.type === 0 // UserType.STUDENT
    }

    /**
     * Check if the user is a teacher
     */
    isTeacher(): boolean {
        return this.type === 1 // UserType.TEACHER
    }

    /**
     * Check if the user belongs to a specific team
     */
    hasTeam(teamId: number): boolean {
        return this.teams.some(t => t.id === teamId)
    }

    toJSON(): RawUser {
        return {
            id: this.id,
            firstName: this.firstName,
            middleName: this.middleName,
            lastName: this.lastName,
            type: this.type,
            avatarUrl: this.avatarUrl,
            teams: this.teams.map(t => t.toJSON()),
        }
    }
}

/**
 * Represents an elective course
 */
export class Elective {
    /** The elective's unique ID */
    readonly id: number
    /** The elective's name */
    readonly name: string
    /** Start date of the selection period (optional) */
    readonly startDate?: Date
    /** End date of the selection period (optional) */
    readonly endDate?: Date
    /** The team ID this elective belongs to */
    readonly teamId: number | null
    /** Subjects within this elective. `null` if not fetched. */
    subjects: Subject[] | null = null

    constructor(data: RawElective) {
        this.id = data.id
        this.name = data.name
        this.startDate = data.startDate ? new Date(data.startDate * 1000) : undefined
        this.endDate = data.endDate ? new Date(data.endDate * 1000) : undefined
        this.teamId = data.teamId ?? null
    }

    /**
     * Check if the elective selection period is currently active
     */
    isSelectionOpen(): boolean {
        const now = Date.now()

        if (this.startDate && now < this.startDate.getTime()) {
            return false
        }

        if (this.endDate && now > this.endDate.getTime()) {
            return false
        }

        return true
    }

    /**
     * Get time until selection opens (returns 0 if already open or `null` if no start date)
     */
    getTimeUntilOpen(): number | null {
        if (!this.startDate) return null
        const diff = this.startDate.getTime() - Date.now()
        return Math.max(0, diff)
    }

    /**
     * Get time until selection closes (returns 0 if already closed or `null` if no end date)
     */
    getTimeUntilClose(): number | null {
        if (!this.endDate) return null
        const diff = this.endDate.getTime() - Date.now()
        return Math.max(0, diff)
    }

    toJSON(): RawElective {
        return {
            id: this.id,
            name: this.name,
            startDate: this.startDate ? Math.floor(this.startDate.getTime() / 1000) : undefined,
            endDate: this.endDate ? Math.floor(this.endDate.getTime() / 1000) : undefined,
            teamId: this.teamId ?? undefined,
        }
    }
}

/**
 * Represents a subject within an elective
 */
export class Subject {
    /** The subject's unique ID */
    readonly id: number
    /** The subject's name */
    readonly name: string
    /** The subject's description (optional) */
    readonly description?: string
    /** The subject's code */
    readonly code: string
    /** The subject's tag/category */
    readonly tag: SubjectTag
    /** The subject's location */
    readonly location: string
    /** Maximum capacity for this subject */
    readonly capacity: number
    /** The team ID this subject is restricted to (optional) */
    readonly teamId?: number
    /** Teachers assigned to this subject */
    readonly teachers: User[]
    /** URL to the subject's thumbnail image (optional) */
    readonly thumbnailUrl?: string
    /** URL to the subject's image (optional) */
    readonly imageUrl?: string

    constructor(data: RawSubject) {
        this.id = data.id
        this.name = data.name
        this.description = data.description
        this.code = data.code
        this.tag = data.tag
        this.location = data.location
        this.capacity = data.capacity
        this.teamId = data.teamId
        this.teachers = data.teachers.map(t => new User(t))
        this.thumbnailUrl = data.thumbnailUrl
        this.imageUrl = data.imageUrl
    }

    canUserEnroll(user: User): boolean {
        if (!user.isStudent()) return false

        if (this.teamId != null && !user.hasTeam(this.teamId)) {
            return false
        }

        return true
    }

    isUserTeaching(user: User): boolean {
        if (!user.isTeacher()) return false
        return this.teachers.some(t => t.id === user.id)
    }

    toJSON(): RawSubject {
        return {
            id: this.id,
            name: this.name,
            description: this.description,
            code: this.code,
            tag: this.tag,
            location: this.location,
            capacity: this.capacity,
            teamId: this.teamId,
            teachers: this.teachers.map(t => t.toJSON()),
        }
    }
}
