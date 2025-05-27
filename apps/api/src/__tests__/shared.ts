import { AuthService_AuthenticateRequest, AuthService_AuthenticateResponse } from '@bodin2/electives-proto/api'

export const BaseUrl = `http://${process.env.ELECTIVES_API_HOSTNAME ?? 'localhost'}:${process.env.ELECTIVES_API_PORT ?? 3000}`

export const Credentials = {
    id: 23151,
    password: 'password',
} as AuthService_AuthenticateRequest

try {
    await fetch(BaseUrl)
} catch {
    console.error('Server is not running. Please start the server before running tests.')
    process.exit(1)
}

export async function authenticate(): Promise<[token: string | undefined, response: Response]> {
    const { default: AuthService } = await import('../services/auth')
    const res = await fetch(`${BaseUrl}${AuthService.Group}`, {
        method: 'POST',
        body: AuthService_AuthenticateRequest.encode(Credentials).finish(),
        headers: {
            'Content-Type': 'application/x-protobuf',
        },
    })

    return [AuthService_AuthenticateResponse.decode(new Uint8Array(await res.arrayBuffer())).token, res]
}
