import { type RawElective, type RawSubject, type RawTeam, type RawUser, type SubjectTag, UserType } from './types'

export class Team {
    readonly id: number
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

export class User {
    readonly id: number
    readonly firstName: string
    readonly middleName?: string
    readonly lastName: string
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
        return this.type === UserType.STUDENT
    }

    /**
     * Check if the user is a teacher
     */
    isTeacher(): boolean {
        return this.type === UserType.TEACHER
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

export class Elective {
    readonly id: number
    readonly name: string
    readonly startDate?: Date
    readonly endDate?: Date
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

export class Subject {
    readonly id: number
    readonly name: string
    readonly description?: string
    readonly code: string
    readonly tag: SubjectTag
    readonly location: string
    readonly capacity: number
    readonly teamId?: number
    /** Teachers assigned to this subject */
    readonly teachers: User[]
    readonly thumbnailUrl?: string
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
            thumbnailUrl: this.thumbnailUrl,
            imageUrl: this.imageUrl,
        }
    }
}
