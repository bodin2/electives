import { Elysia } from 'elysia'
import { protobuf } from 'elysia-protobuf'

const app = new Elysia()
    .use(
        protobuf({
            schemas: {},
        }),
    )
    .get('/', () => 'Hello')
    .listen(process.env.ELECTIVE_API_PORT ?? 3000)

console.log(`Server is running at ${app.server?.hostname}:${app.server?.port}`)
