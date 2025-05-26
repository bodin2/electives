import { sign as pSign, verify as pVerify } from 'paseto-ts/v4'
import pkg from '../../package.json' with { type: 'json' }

import type { Payload } from 'paseto-ts/lib/types'

export function hash(data: Bun.StringOrBuffer) {
    return Bun.password.hash(data, {
        algorithm: 'bcrypt',
        cost: Number(process.env.ELECTIVES_API_HASH_COST) || 12,
    })
}

export function verifyHash(data: Bun.StringOrBuffer, hash: string) {
    return Bun.password.verify(data, hash, 'bcrypt')
}

export const Audience = pkg.name
export const Issuer = `${pkg.name}@${pkg.version}`

export function sign<T extends object>(
    payload: Omit<Payload, 'aud' | 'iss'> & { aud?: never; iss?: never } & T,
): string {
    return pSign(process.env.ELECTIVES_API_SECRET_KEY, {
        ...payload,
        aud: Audience,
        iss: Issuer,
    })
}

export function verifyToken<T extends object>(token: string): Payload & T {
    try {
        const { payload } = pVerify<T>(process.env.ELECTIVES_API_PUBLIC_KEY, token)
        if (payload.aud !== Audience || payload.iss !== Issuer) throw new Error('Invalid audience or issuer')
        return payload
    } catch (error) {
        throw new Error('Invalid token')
    }
}
