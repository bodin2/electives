import { AuthRequest, AuthResponse, Student } from '@bodin2/electives-proto/api'

import { HttpError } from 'elysia-http-error'
import { protobuf } from 'elysia-protobuf'

import { createStudentToken, fetchStudentByToken } from '../utils/users'

import type bearer from '@elysiajs/bearer'
import type Elysia from 'elysia'

export const AuthServiceGroup = '/auth'

const AuthService = (app: Elysia) =>
    app
        .use(
            protobuf({
                schemas: {
                    request: AuthRequest,
                    response: AuthResponse,
                    user: Student,
                },
            }),
        )
        .group(AuthServiceGroup, app =>
            app
                // POST /auth
                .post(
                    '/',
                    async ({ body, decode, set }) => {
                        const { id, password } = await decode('request', body)

                        try {
                            return { token: await createStudentToken(id, password) }
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
                            ({ user }) => {
                                return {
                                    id: user.id,
                                    firstName: user.firstName,
                                    middleName: user.middleName ?? undefined,
                                    lastName: user.lastName,
                                    teamId: user.teamId,
                                } satisfies Student
                            },
                            { parse: 'protobuf', responseSchema: 'user' },
                        ),
                ),
        )

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
