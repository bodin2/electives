import { eq, inArray } from 'drizzle-orm'

import db from '../../db'
import { students, studentsToTeams, teams } from '../../db/schema'

import { type UserInsert, createUser } from '.'

import type { InferInsertType, InferResultType } from '../../db'

export type Student = InferResultType<'students', { user: true }>
export type StudentInsert = InferInsertType<'students'>

export async function createStudent(student: StudentInsert & UserInsert, password: string): Promise<Student> {
    await createUser(student, password)
    await db.insert(students).values(student).returning().get()

    return (await db.query.students.findFirst({
        where: eq(students.id, student.id),
        with: {
            user: true,
        },
    })) as Student
}

export async function getStudentElectives(id: Student['id'], limit = 5, offset = 0) {
    const teamIds = await db.query.studentsToTeams
        .findMany({
            where: eq(studentsToTeams.studentId, id),
            limit,
            offset,
        })
        .then(val => val.map(it => it.teamId))

    return await db.query.electives.findMany({
        where: inArray(teams.id, teamIds),
    })
}
