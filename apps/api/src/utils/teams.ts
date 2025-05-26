import db from '../db'
import { teams } from '../db/schema'

import type { InferInsertType } from '../db'

export function createTeam(team: InferInsertType<'teams'>) {
    return db.insert(teams).values(team).returning().get()
}
