import { and, eq } from 'drizzle-orm'

import db from '../db'
import { electives, subjects } from '../db/schema'

import type { SubjectTag } from '@bodin2/electives-proto/api'
import type { InferResultType } from '../db'
import type { Team } from './teams'

export type Elective = InferResultType<'electives'>

export function createElective(
    id: Elective['id'],
    name: Elective['name'],
    teamId: Team['id'],
    startDate?: Elective['startDate'],
    endDate?: Elective['endDate'],
): Elective {
    return db
        .insert(electives)
        .values({
            id,
            name,
            teamId,
            startDate,
            endDate,
        })
        .returning()
        .get()
}

export async function getElectiveSubjects(
    id: Elective['id'],
    tag?: SubjectTag,
    limit = 100,
    offset = 0,
): Promise<InferResultType<'subjects'>[]> {
    const elective = await db.query.electives.findFirst({ where: eq(electives.id, id) })
    if (!elective) throw new ReferenceError(`Elective ${id} not found`)

    const eltFtr = eq(subjects.electiveId, id)
    return await db.query.subjects.findMany({
        where: tag === undefined ? eltFtr : and(eltFtr, eq(subjects.tag, tag)),
        limit,
        offset,
    })
}

export async function getAllElectives(limit = 5, offset = 0): Promise<Elective[]> {
    return await db.query.electives.findMany({ limit, offset })
}
