import { Database } from 'bun:sqlite'
import { drizzle } from 'drizzle-orm/bun-sqlite'

import * as schema from './schema'

// TODO(apps/api): add test-only DB based on NODE_ENV
const sqlite = new Database(process.env.ELECTIVES_API_DB, {
    strict: true,
    readwrite: true,
    create: true,
})

// TODO(apps/api): run based on NODE_ENV
sqlite.run('PRAGMA journal_mode = WAL;')

const db = drizzle({ client: sqlite, schema: schema })

export default db

// https://github.com/drizzle-team/drizzle-orm/issues/695#issuecomment-1881454650

import type { BuildQueryResult, DBQueryConfig, ExtractTablesWithRelations } from 'drizzle-orm'

type Schema = typeof schema
type TSchema = ExtractTablesWithRelations<Schema>

export type InferInsertType<TableName extends keyof TSchema> = Schema[TableName]['$inferInsert']

export type IncludeRelation<TableName extends keyof TSchema> = DBQueryConfig<
    'one' | 'many',
    boolean,
    TSchema,
    TSchema[TableName]
>['with']

export type IncludeColumns<TableName extends keyof TSchema> = DBQueryConfig<
    'one' | 'many',
    boolean,
    TSchema,
    TSchema[TableName]
>['columns']

export type InferResultType<
    TableName extends keyof TSchema,
    With extends IncludeRelation<TableName> | undefined = undefined,
    Columns extends IncludeColumns<TableName> | undefined = undefined,
> = BuildQueryResult<
    TSchema,
    TSchema[TableName],
    {
        columns: Columns
        with: With
    }
>
