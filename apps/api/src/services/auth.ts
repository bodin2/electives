import {
    AuthService_AuthenticateRequest,
    AuthService_AuthenticateResponse,
    User,
    UserType,
} from '@bodin2/electives-proto/api'

import { Elysia } from 'elysia'
import { HttpError } from 'elysia-http-error'
import { protobuf } from 'elysia-protobuf'

import { createUserToken, fetchUserByToken } from '../utils/users'

import type bearer from '@elysiajs/bearer'

const AuthService = () =>
    new Elysia({ prefix: AuthService.Group })
        .use(
            protobuf({
                schemas: {
                    authRequest: AuthService_AuthenticateRequest,
                    auth: AuthService_AuthenticateResponse,
                    whoami: User,
                },
            }),
        )
        // POST /auth
        .post(
            '/',
            async ({ body, decode, set }) => {
                const { id, password } = await decode('authRequest', body)

                try {
                    return { token: await createUserToken(id, password) } satisfies AuthService_AuthenticateResponse
                } catch {
                    set.status = 401
                    throw HttpError.Unauthorized()
                }
            },
            {
                parse: 'protobuf',
                responseSchema: 'auth',
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
                            type: user.student ? UserType.STUDENT : UserType.TEACHER,
                        }) satisfies User,
                    { parse: 'protobuf', responseSchema: 'whoami' },
                ),
        )

AuthService.Group = '/auth'

export function authenticator(app: ReturnType<typeof bearer>) {
    return app.derive(async ({ bearer: token }) => {
        const err = HttpError.Unauthorized('Unauthorized')
        if (typeof token !== 'string') throw err

        try {
            return { user: await fetchUserByToken(token) }
        } catch {
            throw err
        }
    })
}

export default AuthService
