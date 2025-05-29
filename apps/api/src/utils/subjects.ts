import type { InferResultType } from '../db'
import db from '../db'
import { subjects } from '../db/schema'

export type Subject = InferResultType<'subjects'>

export function createSubject(
    id: Subject['id'],
    electiveId: Subject['electiveId'],
    name: string,
    description: string,
    location: string,
    maxStudents: number,
    tag: Subject['tag'],
    teamId?: Subject['teamId'],
): Subject {
    return db
        .insert(subjects)
        .values({
            id,
            electiveId,
            name,
            description,
            location,
            maxStudents,
            tag,
            teamId,
        })
        .returning()
        .get()
}
