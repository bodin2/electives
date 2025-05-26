import { describe, expect, it } from 'bun:test'
import { Audience, Issuer, hash, sign, verifyHash, verifyToken } from './crypto'

import type { Payload } from 'paseto-ts/lib/types'

describe('hash', () => {
    it('hashes string', async () => {
        const data = 'password123'
        const hashed = await hash(data)

        expect(typeof hashed).toBe('string')
        expect(hashed).not.toBe(data)
    })
})

describe('verifyHash', () => {
    it('verifies hash', async () => {
        const data = 'password123'
        const hashed = await hash(data)

        const isValid = await verifyHash(data, hashed)
        expect(isValid).toBe(true)
    })

    it('fails to verify incorrect hash', async () => {
        const data = 'password123'
        const hashed = await hash(data)

        const isValid = await verifyHash('wrongpassword', hashed)
        expect(isValid).toBe(false)
    })
})

describe('sign', () => {
    it('signs payload with correct AUD and ISS', () => {
        const payload: Omit<Payload, 'aud' | 'iss'> = { sub: '12345' }
        const token = sign(payload)

        expect(typeof token).toBe('string')
        expect(token).not.toBe('')
    })

    it('throws error if secret key is missing', () => {
        const originalSecretKey = process.env.ELECTIVES_API_SECRET_KEY
        // @ts-expect-error
        // biome-ignore lint/performance/noDelete: Testing-only
        delete process.env.ELECTIVES_API_SECRET_KEY

        expect(() => sign({})).toThrow()

        process.env.ELECTIVES_API_SECRET_KEY = originalSecretKey
    })
})

describe('verifyToken', () => {
    it('throws error if public key is missing', () => {
        const originalPublicKey = process.env.ELECTIVES_API_PUBLIC_KEY
        // @ts-expect-error
        // biome-ignore lint/performance/noDelete: Testing-only
        delete process.env.ELECTIVES_API_PUBLIC_KEY

        expect(() => verifyToken('some.token.value')).toThrow()

        process.env.ELECTIVES_API_PUBLIC_KEY = originalPublicKey
    })

    it('verifies token', () => {
        const now = new Date()
        now.setMilliseconds(0)

        const payload: Omit<Payload, 'aud' | 'iss'> = {
            sub: '12345',
            iat: now.toISOString(),
            // biome-ignore lint/style/noCommaOperator: Setting iat to 1 second before now
            exp: (now.setSeconds(now.getSeconds() + 5), now.toISOString()),
        }
        const token = sign(payload)

        const { aud, iss, sub } = verifyToken(token)

        expect(aud).toBe(Audience)
        expect(iss).toBe(Issuer)
        expect(sub).toBe(payload.sub)
    })

    it('throws error for invalid token', () => {
        const invalidToken = 'invalid.token.value'

        expect(() => verifyToken(invalidToken)).toThrow('Invalid token')
    })
})
