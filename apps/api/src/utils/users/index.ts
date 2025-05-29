import { eq } from 'drizzle-orm'

import db from '../../db'
import { students, users } from '../../db/schema'

import { hash, sign, verifyHash, verifyToken } from '../crypto'

import type { InferInsertType, InferResultType } from '../../db'

export type User = InferResultType<'users', { student: true; teacher: true }>
// integer().primaryKey() in SQLite makes columns auto-incrementing, so the field in InsertType is actually optional, but we don't want that
export type UserInsert = OmitSecurityFields<InferInsertType<'users'> & { id: User['id'] }>
type OmitSecurityFields<T> = Omit<T, 'hash' | 'sessionHash'>

export const SessionDuration = Number(process.env.ELECTIVES_API_SESSION_DURATION) || 86_400_000
export const InvalidCredentialsError = new Error('Invalid credentials')

export interface TokenPayload {
    sid: string
}

/**
 * This should not be called directly, use `createStudent` or `createTeacher` instead.
 */
export async function createUser(user: OmitSecurityFields<UserInsert>, password: string) {
    return db
        .insert(users)
        .values({
            ...user,
            hash: await hash(password),
            sessionHash: null,
        })
        .returning()
        .get()
}

export async function createUserToken(id: User['id'], password: string) {
    const student = await db.query.users.findFirst({
        where: eq(students.id, id),
        columns: { hash: true },
    })

    if (student && (await verifyHash(password, student.hash))) {
        const iat = new Date()
        // Remove fractional seconds as it is not recommended by PASETO standard
        iat.setMilliseconds(0)

        const sid = Bun.randomUUIDv7('base64url', iat)
        const token = sign<TokenPayload>({
            sub: id.toString(),
            exp: new Date(iat.getTime() + SessionDuration).toISOString(),
            iat: iat.toISOString(),
            sid,
        })

        await db
            .update(users)
            .set({ sessionHash: await hash(sid) })
            .where(eq(users.id, id))
        return token
    }

    throw InvalidCredentialsError
}

export async function fetchUserByToken(token: string): Promise<User> {
    const { sub, sid } = verifyToken<TokenPayload>(token)

    if (sid) {
        const user = await db.query.users.findFirst({
            where: eq(students.id, Number(sub)),
            with: {
                student: true,
                teacher: true,
            },
        })

        if (user?.sessionHash && (await verifyHash(sid, user.sessionHash))) return user
    }

    throw InvalidCredentialsError
}
