import { User } from '@bodin2/electives-proto/api'

import { describe, expect, test } from 'bun:test'

import { BaseUrl, Credentials, authenticate } from '../__tests__/shared'
import UsersService from './users'

const [token] = await authenticate()

describe.skipIf(!token)(UsersService.Group, () => {
    const BaseRoute = `${BaseUrl}${UsersService.Group}`
    const Route = `${BaseRoute}/${Credentials.id}`
    const MeRoute = `${BaseRoute}/@me`

    describe('/users/@me', () => {
        test('GET -> 200', async () => {
            const res = await fetch(MeRoute, {
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            })

            expect(res.status).toBe(200)

            const stud = User.decode(new Uint8Array(await res.arrayBuffer()))

            expect(stud.id).toEqual(Credentials.id)
            expect(stud).toHaveProperty('firstName')
            expect(stud).toHaveProperty('lastName')
        })

        test('GET -> 401 (no token)', () => expect(fetch(MeRoute)).resolves.toHaveProperty('status', 401))
    })

    describe('/users/:id', () => {
        test('GET -> 200', async () => {
            const res = await fetch(Route, {
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            })

            expect(res.status).toBe(200)

            const stud = User.decode(new Uint8Array(await res.arrayBuffer()))

            expect(stud.id).toEqual(Credentials.id)
            expect(stud).toHaveProperty('firstName')
            expect(stud).toHaveProperty('lastName')
        })

        test('GET -> 404 (no user)', () =>
            expect(
                fetch(`${BaseRoute}/-1`, {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }),
            ).resolves.toHaveProperty('status', 404))

        test('GET -> 400 (invalid id)', () =>
            expect(
                fetch(`${BaseRoute}/invalid-id`, {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }),
            ).resolves.toHaveProperty('status', 400))

        test('GET -> 401 (no token)', () => expect(fetch(Route)).resolves.toHaveProperty('status', 401))
    })
})
