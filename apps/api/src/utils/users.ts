import { eq } from 'drizzle-orm'

import db from '../db'
import { students } from '../db/schema'

import { hash, sign, verifyHash, verifyToken } from './crypto'

import type { InferResultType } from '../db'

export type StudentModel = InferResultType<'students'>

export const SessionDuration = 1000 * 60 * 60 * 24 // 1 day in milliseconds

export async function createStudent(student: Omit<StudentModel, 'hash' | 'sessionIAt'>, password: string) {
    return db
        .insert(students)
        .values({
            ...student,
            hash: await hash(password),
        })
        .returning()
        .get()
}

interface StudentTokenPayload {
    sid: string
}

export async function createStudentToken(id: StudentModel['id'], password: string) {
    const student = await db.query.students.findFirst({
        where: eq(students.id, id),
        columns: { hash: true },
    })

    if (student && (await verifyHash(password, student.hash))) {
        const iat = new Date()
        // Remove fractional seconds as it is not recommended by PASETO standard
        iat.setMilliseconds(0)

        const sid = Bun.randomUUIDv7('base64url', iat)
        const token = sign<StudentTokenPayload>({
            sub: id.toString(),
            exp: new Date(iat.getTime() + SessionDuration).toISOString(),
            iat: iat.toISOString(),
            sid,
        })

        await db
            .update(students)
            .set({ sessionHash: await hash(sid) })
            .where(eq(students.id, id))
        return token
    }

    throw new Error('Invalid credentials')
}

export async function fetchStudentByToken(token: string): Promise<StudentModel> {
    const { sub, sid } = verifyToken<StudentTokenPayload>(token)

    if (sid) {
        const student = await db.query.students.findFirst({
            where: eq(students.id, Number(sub)),
        })

        if (student?.sessionHash && (await verifyHash(sid, student.sessionHash))) return student
    }

    throw new Error('Invalid token')
}
