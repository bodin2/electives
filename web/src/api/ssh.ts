function base64Decode(str: string): Uint8Array {
    const bin = atob(str)
    const out = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
    return out
}

function base64urlEncode(data: Uint8Array): string {
    let bin = ''
    for (const byte of data) bin += String.fromCharCode(byte)
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function base64urlDecode(str: string): Uint8Array {
    const base64 = str.replace(/-/g, '+').replace(/_/g, '/')
    const pad = base64.length % 4
    return base64Decode(pad ? base64 + '='.repeat(4 - pad) : base64)
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
    const body = pem.replace('-----BEGIN PRIVATE KEY-----', '').replace('-----END PRIVATE KEY-----', '').trim()
    const bytes = base64Decode(body)
    const buf = new ArrayBuffer(bytes.byteLength)
    new Uint8Array(buf).set(bytes)
    return buf
}

const RSA_ALGORITHM: RsaHashedImportParams = {
    name: 'RSASSA-PKCS1-v1_5',
    hash: 'SHA-256',
}

/**
 * Import a PKCS#8 PEM RSA private key for signing.
 *
 * @returns a `CryptoKey` usable with {@link signChallenge}
 */
export async function importPrivateKey(pem: string): Promise<CryptoKey> {
    return crypto.subtle.importKey('pkcs8', pemToArrayBuffer(pem), RSA_ALGORITHM, false, ['sign'])
}

/**
 * Sign a base64url-encoded challenge using the given RSA key.
 *
 * @param key CryptoKey obtained from {@link importPrivateKey}
 * @param challenge base64url-encoded challenge string from the server
 *
 * @returns base64url-encoded signature (no padding)
 */
export async function signChallenge(key: CryptoKey, challenge: string): Promise<string> {
    const data = base64urlDecode(challenge)
    const buf = new ArrayBuffer(data.byteLength)
    new Uint8Array(buf).set(data)
    const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, buf)
    return base64urlEncode(new Uint8Array(sig))
}
