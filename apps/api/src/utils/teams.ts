import db from '../db'
import { teams } from '../db/schema'

import type { InferInsertType } from '../db'

export type Team = InferInsertType<'teams'>

export function createTeam(team: Team): Team {
    return db.insert(teams).values(team).returning().get()
}
