import { AuthService_AuthenticateRequest, AuthService_AuthenticateResponse } from '@bodin2/electives-proto/api'

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
                },
            }),
        )
        // POST /auth
        .post(
            '/',
            async ({ body, decode }) => {
                const { id, password } = await decode('authRequest', body)

                try {
                    return { token: await createUserToken(id, password) } satisfies AuthService_AuthenticateResponse
                } catch {
                    throw HttpError.Unauthorized()
                }
            },
            {
                parse: 'protobuf',
                responseSchema: 'auth',
            },
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
