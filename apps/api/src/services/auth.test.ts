import { describe, expect, test } from 'bun:test'

import { BaseUrl, authenticate } from '../__tests__/shared'
import AuthService from './auth'

describe(AuthService.Group, () => {
    const Route = `${BaseUrl}${AuthService.Group}`

    test('POST -> 400 (invalid body)', () => {
        expect(
            fetch(Route, {
                method: 'POST',
                body: '',
            }),
        ).resolves.toHaveProperty('status', 400)
    })

    test('POST -> 200 (gens unique tokens) | 401 (invalid credentials)', async () => {
        const [oldToken] = await authenticate()
        const [token, res] = await authenticate()

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
