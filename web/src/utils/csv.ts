import { Group as GroupProto, GroupType, User as ProtoUser } from '@bodin2/electives-common/proto/api'
import { inferSchema, initParser } from 'udsv'
import { AdminAddUserRequest, UserType } from '~/api/types'
import { nonNull } from '~/utils'
import type { Group, User } from '~/api'

export class CSVParseError extends Error {
    constructor(
        message: string,
        public readonly row: number,
    ) {
        super(message)
        this.name = 'CSVParseError'
    }
}

/**
 * A group lookup matched multiple groups. Only collected when the ambiguous
 * name is actually referenced by a CSV row.
 */
export class AmbiguousGroupError extends CSVParseError {
    constructor(
        public readonly query: string,
        public readonly groups: Group[],
        row: number,
    ) {
        super(`Ambiguous group "${query}" matched ${groups.length} groups`, row)
        this.name = 'AmbiguousGroupError'
    }
}

/** No group exists matching the given name or id. */
export class InvalidGroupError extends CSVParseError {
    constructor(
        public readonly query: string,
        row: number,
    ) {
        super(`No group matched "${query}"`, row)
        this.name = 'InvalidGroupError'
    }
}

/**
 * A group resolved successfully but its `type` doesn't match the slot it was
 * supplied to (e.g. a CUSTOM group placed in the `grade` column).
 */
export class WrongGroupTypeError extends CSVParseError {
    constructor(
        public readonly query: string,
        public readonly group: Group,
        public readonly expected: GroupType,
        row: number,
    ) {
        super(`Group "${query}" has type ${GroupType[group.type]}, expected ${GroupType[expected]}`, row)
        this.name = 'WrongGroupTypeError'
    }
}

export class InvalidFieldsError extends CSVParseError {
    constructor(row: number) {
        super('Some fields are invalid', row)
        this.name = 'InvalidFieldsError'
    }
}

/**
 * Indexes a list of groups by id and by name, resolving CSV cell values
 * lazily. Same-name collisions are tolerated; an `AmbiguousGroupError` is
 * only emitted if a query actually hits a colliding name.
 */
class GroupResolver {
    private byId = new Map<number, Group>()
    private byName = new Map<string, Group[]>()

    constructor(groups: readonly Group[]) {
        for (const g of groups) {
            this.byId.set(g.id, g)
            const bucket = this.byName.get(g.name)
            if (bucket) bucket.push(g)
            else this.byName.set(g.name, [g])
        }
    }

    /**
     * Resolve a CSV cell value to a group.
     *
     * @param query Trimmed cell value (either a numeric id or a group name)
     * @param row 1-indexed data row for error reporting
     * @param errors Sink for collected errors
     * @param expectedType If set, the resolved group's `type` must match
     */
    resolve(query: string, row: number, errors: Error[], expectedType?: GroupType): Group | undefined {
        if (!query) return undefined

        // User ID
        if (/^\d+$/.test(query)) {
            const id = Number.parseInt(query, 10)
            const byId = this.byId.get(id)
            if (byId) return this.checkType(byId, query, row, errors, expectedType)
            errors.push(new InvalidGroupError(query, row))
            return undefined
        }

        // Name
        const matches = this.byName.get(query)
        if (!matches || matches.length === 0) {
            errors.push(new InvalidGroupError(query, row))
            return undefined
        }

        // If we have a type constraint, narrow before judging ambiguity so a
        // disambiguating type lets us pick the right group cleanly
        const candidates = expectedType !== undefined ? matches.filter(g => g.type === expectedType) : matches

        if (candidates.length === 0) {
            errors.push(new WrongGroupTypeError(query, matches[0], nonNull(expectedType), row))
            return undefined
        }
        if (candidates.length > 1) {
            errors.push(new AmbiguousGroupError(query, candidates, row))
            return undefined
        }
        return candidates[0]
    }

    private checkType(
        group: Group,
        query: string,
        row: number,
        errors: Error[],
        expectedType?: GroupType,
    ): Group | undefined {
        if (expectedType !== undefined && group.type !== expectedType) {
            errors.push(new WrongGroupTypeError(query, group, expectedType, row))
            return undefined
        }
        return group
    }
}

const SLOTTED_HEADERS: Record<string, GroupType> = {
    grade: GroupType.GRADE,
    grade_id: GroupType.GRADE,
    room: GroupType.ROOM,
    room_id: GroupType.ROOM,
    program: GroupType.PROGRAM,
    program_id: GroupType.PROGRAM,
}

/**
 * Parse a CSV file into AdminAddUserRequest objects.
 *
 * Supported columns (case-insensitive, header names trimmed):
 * - `id`
 * - `prefix`
 * - `first_name` / `firstname`
 * - `middle_name` / `middlename`
 * - `last_name` / `lastname`
 * - `password`
 * - `grade` / `grade_id`
 * - `room` / `room_id`
 * - `program` / `program_id`
 * - `groups` / `group_ids` (comma-separated; CUSTOM groups only)
 *
 * Group cells accept either a numeric id or an exact group name. If multiple
 * groups share a name, referencing that name raises `AmbiguousGroupError`.
 * Unknown groups raise `InvalidGroupError`. Wrong-type assignments (e.g. a
 * CUSTOM group in the `grade` column) raise `WrongGroupTypeError`. All
 * collected errors are rethrown as a single `AggregateError`.
 *
 * @param text The CSV text content
 * @param type The user type to assign
 * @param cachedGroups A list of all known groups, used for lookup.
 *
 * @returns An array of user creation requests
 *
 * @throws {AggregateError} if any lookups fail
 */
export function parseUserCSV(text: string, type: UserType, cachedGroups?: Group[]): AdminAddUserRequest[] {
    try {
        const parser = initParser(inferSchema(text, { trim: true }))
        const rows = parser.stringObjs<{ [header: string]: string }>(text)

        const resolver = new GroupResolver(cachedGroups ?? [])
        const errors: Error[] = []
        const requests: AdminAddUserRequest[] = []

        for (let i = 0; i < rows.length; i++) {
            const rowNum = i + 1
            const row = rows[i]

            const user = ProtoUser.create({ type, groups: [] })
            const req = AdminAddUserRequest.create({ groupIds: [], password: '' })

            for (const key in row) {
                const header = key.trim().toLowerCase()
                const value = row[key]?.trim()
                if (!value) continue

                switch (header) {
                    case 'id':
                        user.id = Number.parseInt(value, 10)
                        break
                    case 'prefix':
                        user.prefix = value
                        break
                    case 'first_name':
                    case 'firstname':
                        user.firstName = value
                        break
                    case 'middle_name':
                    case 'middlename':
                        user.middleName = value
                        break
                    case 'last_name':
                    case 'lastname':
                        user.lastName = value
                        break
                    case 'password':
                        req.password = value
                        break
                    case 'grade':
                    case 'grade_id':
                    case 'room':
                    case 'room_id':
                    case 'program':
                    case 'program_id': {
                        const slotType = SLOTTED_HEADERS[header]
                        const group = resolver.resolve(value, rowNum, errors, slotType)
                        if (!group) break
                        if (slotType === GroupType.GRADE) req.gradeId = group.id
                        else if (slotType === GroupType.ROOM) req.roomId = group.id
                        else req.programId = group.id
                        user.groups.push(GroupProto.fromJSON(group))
                        break
                    }
                    case 'groups':
                    case 'group_ids': {
                        for (const raw of value.split(',')) {
                            const query = raw.trim()
                            if (!query) continue
                            const group = resolver.resolve(query, rowNum, errors, GroupType.CUSTOM)
                            if (!group) continue
                            req.groupIds.push(group.id)
                            user.groups.push(GroupProto.fromJSON(group))
                        }
                        break
                    }
                }
            }

            req.user = user

            let errored = false

            if (user.id === undefined || Number.isNaN(user.id) || !user.firstName || !req.password) {
                errors.push(new InvalidFieldsError(rowNum))
                errored = true
            }

            if (user.type === UserType.STUDENT && (req.gradeId && req.roomId) === undefined) {
                errors.push(new InvalidFieldsError(rowNum))
                errored = true
            }

            if (!errored) requests.push(req)
        }

        if (errors.length > 0) {
            throw new AggregateError(errors, `Failed to parse CSV: ${errors.length} error(s)`)
        }

        return requests
    } catch (err) {
        if (err instanceof TypeError) {
            throw new CSVParseError('Malformed CSV file', 0)
        }
        throw err
    }
}

export type CSVSortKey = 'room' | 'id' | 'firstName'

export function exportStudentsCSV(students: User[], sortBy: CSVSortKey) {
    // biome-ignore lint/suspicious/useIterableCallbackReturn: All cases handled
    const sorted = [...students].sort((a, b) => {
        switch (sortBy) {
            case 'room':
                return (a.room?.name ?? '').localeCompare(b.room?.name ?? '') || a.id - b.id
            case 'id':
                return a.id - b.id
            case 'firstName':
                return a.firstName.localeCompare(b.firstName)
        }
    })

    const header = ['room', 'id', 'prefix', 'first_name', 'last_name']
    const rows = sorted.map(s => [
        s.room?.name ?? '',
        String(s.id),
        s.prefix ?? '',
        s.firstName,
        [s.middleName, s.lastName].filter(Boolean).join(' '),
    ])

    const csv = [header, ...rows].map(row => row.map(csvEscape).join(',')).join('\n')
    return csv
}

function csvEscape(value: string): string {
    if (/[",\n\r]/.test(value)) return `"${value.replace(/"/g, '""')}"`
    return value
}
