import { AuthenticateRequest, AuthenticateResponse, type LoginOptions, RawUser, UnauthorizedError } from './types'
import type { Gateway, GatewayAuthenticator } from './gateway'
import type { RESTClient } from './rest'

/**
 * Handles authentication for both REST (token lifecycle) and Gateway (identify).
 * Implement this interface to support different authentication strategies.
 */
export interface Authenticator<TCredentials> extends GatewayAuthenticator {
    /**
     * Authenticate with the server using the provided credentials.
     *
     * Implementations must store the resulting token so that
     * subsequent calls to `token` and `identify` reflect the new session.
     */
    login(credentials: TCredentials): Promise<string>
    /**
     * Invalidate the current session server-side (if applicable) and
     * clear any stored token.
     */
    logout(): Promise<void>
    /**
     * Check if there is an existing valid session.
     * Should be called on client initialization to determine if the user is already logged in.
     */
    hasSession(): Promise<boolean>
    /**
     * The current authentication token, or `null` if not authenticated
     */
    readonly token: string | null
    /**
     * Set token value for session resume flows
     */
    setToken(token: string | null): void
}

export class UserAuthenticator implements Authenticator<LoginOptions> {
    private _token: string | null = null

    constructor(private readonly rest: RESTClient) {}

    get token(): string | null {
        return this._token
    }

    setToken(token: string | null): void {
        this._token = token
    }

    async login(options: LoginOptions): Promise<string> {
        const { id, password, clientName = 'API Client' } = options

        const response = await this.rest.post<AuthenticateResponse>(
            '/auth',
            { id, password, clientName },
            {
                encoder: AuthenticateRequest,
                decoder: AuthenticateResponse,
            },
        )

        this._token = response.token
        return response.token
    }

    async logout(): Promise<void> {
        await this.rest.post('/logout')
        this._token = null
    }

    async hasSession(): Promise<boolean> {
        try {
            await this.rest.get<RawUser>('/users/@me', { decoder: RawUser })
            return true
        } catch (e) {
            if (e instanceof UnauthorizedError) return false
            throw e
        }
    }

    identify(gateway: Gateway): void {
        const token = this._token
        if (!token) throw new Error('No token available for gateway authentication')

        gateway.send({
            messageId: gateway.nextMessageId(),
            identify: { token },
        })
    }

    refresh(): this {
        return this
    }
}
