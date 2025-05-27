import { AuthService_Request, AuthService_Response, Student } from '@bodin2/electives-proto/api'

import { Elysia } from 'elysia'
import { HttpError } from 'elysia-http-error'
import { protobuf } from 'elysia-protobuf'

import { createStudentToken, fetchStudentByToken } from '../utils/users'

import type bearer from '@elysiajs/bearer'

const AuthService = () =>
    new Elysia({ prefix: AuthService.Group })
        .use(
            protobuf({
                schemas: {
                    request: AuthService_Request,
                    response: AuthService_Response,
                    user: Student,
                },
            }),
        )
        // POST /auth
        .post(
            '/',
            async ({ body, decode, set }) => {
                const { id, password } = await decode('request', body)

                try {
                    return { token: await createStudentToken(id, password) } satisfies AuthService_Response
                } catch {
                    set.status = 401
                    throw HttpError.Unauthorized()
                }
            },
            {
                parse: 'protobuf',
                responseSchema: 'response',
            },
        )
        .group('/whoami', app =>
            app
                .use(authenticator)
                // GET /auth/whoami
                .get(
                    '/',
                    ({ user }) =>
                        ({
                            id: user.id,
                            firstName: user.firstName,
                            middleName: user.middleName ?? undefined,
                            lastName: user.lastName,
                        }) satisfies Student,
                    { parse: 'protobuf', responseSchema: 'user' },
                ),
        )

AuthService.Group = '/auth'

export function authenticator(app: ReturnType<typeof bearer>) {
    return app.derive(async ({ bearer: token }) => {
        const err = HttpError.Unauthorized('Unauthorized')
        if (typeof token !== 'string') throw err

        try {
            return { user: await fetchStudentByToken(token) }
        } catch {
            throw err
        }
    })
}

export default AuthService
