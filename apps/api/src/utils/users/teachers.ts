import { eq } from 'drizzle-orm'

import db from '../../db'
import { teachers, teachersToSubjects } from '../../db/schema'

import { type UserInsert, createUser } from '.'

import type { InferInsertType, InferResultType } from '../../db'
import type { Subject } from '../subjects'

export type Teacher = InferResultType<'teachers', { user: true }>
export type TeacherInsert = InferInsertType<'teachers'>

export async function createTeacher(teacher: TeacherInsert & UserInsert, password: string): Promise<Teacher> {
    await createUser(teacher, password)
    await db.insert(teachers).values(teacher).returning().get()

    return (await db.query.teachers.findFirst({
        where: eq(teachers.id, teacher.id),
        with: {
            user: true,
        },
    })) as Teacher
}

export async function getTeacherOwnedSubjects(id: Teacher['id']): Promise<Subject[]> {
    return await db.query.teachersToSubjects
        .findMany({
            where: eq(teachersToSubjects.teacherId, id),
            with: {
                subject: true,
            },
        })
        .then(val => val.map(it => it.subject))
}
