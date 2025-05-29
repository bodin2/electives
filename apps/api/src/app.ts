import { Elysia } from 'elysia'

import bearer from '@elysiajs/bearer'
import cors from '@elysiajs/cors'
import { helmet } from 'elysia-helmet'
import { HttpError, httpError } from 'elysia-http-error'
import { ip } from 'elysia-ip'
import { ProtoRequestError, ProtoResponseError } from 'elysia-protobuf'
import { type Generator, rateLimit } from 'elysia-rate-limit'

import AuthService from './services/auth'
import ElectivesService from './services/electives'
import StatusService from './services/status'

if (!process.env.ELECTIVES_API_CORS_ORIGIN)
    process.emitWarning('ELECTIVES_API_CORS_ORIGIN is not set, accepting all origins')

export const app = new Elysia()
    .use(
        // @ts-expect-error: ?
        httpError({
            returnStringOnly: true,
        }),
    )
    .use(helmet())
    .use(
        cors({
            origin: process.env.ELECTIVES_API_CORS_ORIGIN ?? '*',
            methods: ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'],
        }),
    )
    .use(ip())
    .use(bearer())
    .use(
        rateLimit({
            max: Number(process.env.ELECTIVES_API_RATE_LIMIT_AMOUNT) || 120,
            duration: Number(process.env.ELECTIVES_API_RATE_LIMIT_WINDOW) || 60_000,
            errorResponse: new Response('Too Many Requests', {
                status: 429,
            }),
            generator: <Generator<{ ip: string }>>((_req, _serv, ctx) => ctx.ip),
            countFailedRequest: true,
        }),
    )
    .error({
        PROTO_REQ_ERROR: ProtoRequestError,
        PROTO_RES_ERROR: ProtoResponseError,
    })
    .onError(({ code, error, request, set }) => {
        try {
            switch (code) {
                case 'INVALID_FILE_TYPE':
                case 'INVALID_COOKIE_SIGNATURE':
                case 'VALIDATION':
                case 'PARSE':
                case 'PROTO_REQ_ERROR':
                    throw HttpError.BadRequest()

                case 'NOT_FOUND':
                    throw HttpError.NotFound()

                case 'PROTO_RES_ERROR':
                    console.trace(`ProtoResponseError (${request.url}):`, error)
                    throw HttpError.Internal()

                default:
                    console.trace(`Error (${request.url}):`, error)
                    throw HttpError.Internal()
            }
        } catch (e) {
            const err = e as HttpError
            set.status = err.statusCode
            return err.message
        }
    })
    .use(StatusService())
    .use(AuthService())
    .use(ElectivesService())

app.listen(
    {
        hostname: process.env.ELECTIVES_API_HOSTNAME ?? 'localhost',
        port: process.env.ELECTIVES_API_PORT ?? 3000,
    },
    () => console.info(`API running on ${app.server?.hostname}:${app.server?.port}`),
)

export type App = typeof app
