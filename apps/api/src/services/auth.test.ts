import { AuthRequest, AuthResponse, Student } from '@bodin2/electives-proto/api'

import { describe, expect, test } from 'bun:test'

import { BaseUrl } from '../__tests__/shared'
import { AuthServiceGroup } from './auth'

const Credentials = {
    id: 23151,
    password: 'password',
} as AuthRequest

describe('/auth', () => {
    const Route = `${BaseUrl}/${AuthServiceGroup}`

    test('POST -> 400 (invalid body)', () => {
        expect(
            fetch(Route, {
                method: 'POST',
                body: '',
            }),
        ).resolves.toHaveProperty('status', 400)
    })

    test('POST -> 200 | 401 (invalid credentials)  +  unique tokens', async () => {
        const body = AuthRequest.encode(Credentials).finish()
        const fetchAuth = () =>
            fetch(Route, {
                method: 'POST',
                body,
                headers: {
                    'Content-Type': 'application/x-protobuf',
                },
            })

        const oldRes = await fetchAuth()
        const res = await fetchAuth()

        const { token: oldToken } = AuthResponse.decode(new Uint8Array(await oldRes.arrayBuffer()))
        const { token } = AuthResponse.decode(new Uint8Array(await res.arrayBuffer()))

        describe('/auth/whoami', async () => {
            const WhoAmIRoute = `${Route}/whoami`

            test.skipIf(res.status !== 200)('GET -> 200', async () => {
                const res = await fetch(WhoAmIRoute, {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                })

                expect(res.status).toBe(200)

                const stud = Student.decode(new Uint8Array(await res.arrayBuffer()))

                expect(stud.id).toEqual(Credentials.id)
                expect(stud).toHaveProperty('firstName')
                expect(stud).toHaveProperty('lastName')
                expect(stud).toHaveProperty('teamId')
            })

            test.skipIf(res.status !== 200)('GET -> 401 (old token)', () => {
                expect(
                    fetch(WhoAmIRoute, {
                        headers: {
                            Authorization: `Bearer ${oldToken}`,
                        },
                    }),
                ).resolves.toHaveProperty('status', 401)
            })

            test('GET -> 401 (no token)', () => {
                return expect(fetch(WhoAmIRoute)).resolves.toHaveProperty('status', 401)
            })
        })

        expect(oldToken).not.toEqual(token)
        expect(res.status).toBeOneOf([200, 401])
    })

    test('ALL -> 404', () => {
        expect(
            fetch(Route, {
                body: '',
                method: 'PUT',
            }),
        ).resolves.toHaveProperty('status', 404)
    })
})
