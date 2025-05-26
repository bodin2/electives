import { defineConfig } from 'drizzle-kit'

export default defineConfig({
    out: './drizzle',
    dialect: 'sqlite',
    schema: './src/db/schema.ts',
    dbCredentials: {
        url: process.env.ELECTIVES_API_DB,
    },
    migrations: {
        prefix: 'timestamp',
        table: '__drizzle_migrations__',
        schema: 'public',
    },
    breakpoints: true,
    // strict: true,
    verbose: true,
})
