import Logger from '@bodin2/electives-common/Logger'
import { useRouteContext } from '@tanstack/solid-router'
import {
    type Accessor,
    createContext,
    createEffect,
    createSignal,
    on,
    onCleanup,
    type ParentComponent,
    useContext,
} from 'solid-js'
import {
    type AdminAuthenticateOptions,
    AdminAuthenticator,
    APIError,
    type Authenticator,
    Client,
    type ClientEventMap,
    Gateway,
    type LoginOptions,
    RESTClient,
    UserAuthenticator,
    UserType,
} from '../api'
import { GatewayEndpoints } from '../api/gateway'
import { NetworkError } from '../api/types'

export enum AuthenticationState {
    Loading = 0,
    LoggedOut = 1,
    LoggedIn = 2,
    NetworkError = 3,
}

interface APIApi {
    client: Client<unknown>
    authState: Accessor<AuthenticationState>
    tokenType: Accessor<TokenType | null>
    login(id: number, password: string): Promise<void>
    adminLogin(key: CryptoKey): Promise<void>
    resumeSession(): Promise<void>
    logout: () => Promise<void>
}

const TOKEN_KEY = 'auth_token'
const TOKEN_TYPE_KEY = 'auth_token_type'
const APIContext = createContext<APIApi>()
const log = new Logger('APIProvider')

export enum TokenType {
    User = 'user',
    Admin = 'admin',
}

type APIClient = Client<unknown>
const gatewayURLFromBaseURL = (baseURL: string, tokenType: TokenType): string => {
    const url = new URL(baseURL)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    url.pathname = tokenType === TokenType.Admin ? GatewayEndpoints.AdminNotifications : GatewayEndpoints.Notifications
    url.search = ''
    url.hash = ''
    return url.toString()
}

const getTokenType = (): TokenType => {
    const raw = localStorage.getItem(TOKEN_TYPE_KEY)
    return raw === TokenType.Admin ? TokenType.Admin : TokenType.User
}

const createAuthenticator = (rest: RESTClient, tokenType: TokenType): Authenticator<unknown> => {
    if (tokenType === TokenType.Admin) {
        return new AdminAuthenticator(rest) as Authenticator<unknown>
    }

    return new UserAuthenticator(rest) as Authenticator<unknown>
}

const configureClientAuth = (client: APIClient, tokenType: TokenType): Authenticator<unknown> => {
    const authenticator = createAuthenticator(client.rest, tokenType)
    client.setAuthenticator(authenticator)
    client.setGatewayURL(gatewayURLFromBaseURL(client.rest.baseURL, tokenType))
    return authenticator
}

export const createClient = () => {
    const baseURL = process.env.API_BASE_URL || 'http://localhost:8080'
    const tokenType = getTokenType()
    const rest = new RESTClient({ baseURL })
    const gateway = new Gateway({
        url: gatewayURLFromBaseURL(baseURL, tokenType),
        maxReconnectAttempts: 3,
        reconnectDelay: 5000,
    })

    return new Client({
        rest,
        gateway,
        authenticator: createAuthenticator(rest, tokenType),
        autoConnect: true,
    })
}

export const initAuth = async (client: APIClient): Promise<AuthenticationState> => {
    const token = localStorage.getItem(TOKEN_KEY)
    const tokenType = getTokenType()
    if (!token) return AuthenticationState.LoggedOut

    log.info('Got token!')

    const authenticator = configureClientAuth(client, tokenType)
    authenticator.setToken(token)

    try {
        await client.resume(token)
        return AuthenticationState.LoggedIn
    } catch (e: unknown) {
        log.error('Failed to login with stored token:', e)

        if (e instanceof APIError && !(e instanceof NetworkError)) {
            return AuthenticationState.LoggedOut
        }

        return AuthenticationState.NetworkError
    }
}

const APIProvider: ParentComponent<{ client: APIClient }> = props => {
    const client = props.client
    const ctx = useRouteContext({ from: '__root__' })

    const [authState, setAuthState] = createSignal(AuthenticationState.Loading)
    const [tokenType, setTokenType] = createSignal<TokenType | null>(
        localStorage.getItem(TOKEN_TYPE_KEY) === TokenType.Admin
            ? TokenType.Admin
            : localStorage.getItem(TOKEN_TYPE_KEY) === TokenType.User
              ? TokenType.User
              : null,
    )
    const [updater, setUpdater] = createSignal(0)

    createEffect(() => {
        log.debug('Authentication state changed to:', AuthenticationState[authState()])
    })

    createEffect(() => {
        ctx().authState.then(state => {
            log.debug('Syncing router auth state:', AuthenticationState[state])
            setAuthState(state)
        })
    })

    let loggingOut = false

    function checkSession(error: Error) {
        loggingOut = true
        client.hasSession().then(hasSession => {
            if (!hasSession) {
                log.warn('Unauthorized, logging out:', error.message)
                return client.logout()
            }

            loggingOut = false
        })
    }

    createEffect(
        on(updater, () => {
            const onReady = (user: ClientEventMap['ready']) => {
                log.info('Logged in as:', user)
                setTokenType(user.type === UserType.ADMIN ? TokenType.Admin : TokenType.User)
                setAuthState(AuthenticationState.LoggedIn)
            }

            const onError = (err: ClientEventMap['error']) => {
                log.error('Client error occurred:', err)
            }

            const onNetworkError = (err: ClientEventMap['networkError']) => {
                log.error('Network error occurred', err)
                if (authState() !== AuthenticationState.LoggedOut) setAuthState(AuthenticationState.NetworkError)
            }

            const onGatewayConnect = () => {
                log.info('Connected to gateway')
            }

            const onGatewayDisconnect = (reason: ClientEventMap['gatewayDisconnect']) => {
                if (reason.code !== 1000)
                    checkSession(new Error(`Gateway disconnected uncleanly: ${reason.reason} (${reason.code})`))

                log.warn('Disconnected from gateway:', reason)
            }

            const onGatewayRateLimited = (retryAfter: ClientEventMap['gatewayRateLimited']) => {
                log.warn('Gateway rate limited, retrying after:', retryAfter, 'ms')
            }

            const onUnauthorized = (error: ClientEventMap['unauthorized']) => {
                if (authState() === AuthenticationState.LoggedOut || loggingOut) {
                    log.warn('Received unauthorized event while logged out, likely a bad session.')
                    return
                }

                checkSession(error)
            }

            const onLogout = () => {
                localStorage.removeItem(TOKEN_KEY)
                localStorage.removeItem(TOKEN_TYPE_KEY)
                setTokenType(null)
                setAuthState(AuthenticationState.LoggedOut)
                setUpdater(~updater())

                log.info('Logged out')
            }

            client.on('ready', onReady)
            client.on('error', onError)
            client.on('networkError', onNetworkError)
            client.on('gatewayConnect', onGatewayConnect)
            client.on('gatewayDisconnect', onGatewayDisconnect)
            client.on('gatewayRateLimited', onGatewayRateLimited)
            client.on('unauthorized', onUnauthorized)
            client.on('logout', onLogout)

            // Handle events that might have already fired
            if (client.user) {
                onReady(client.user)
            }

            if (client.isGatewayConnected()) {
                onGatewayConnect()
            }

            onCleanup(() => {
                client.off('ready', onReady)
                client.off('error', onError)
                client.off('networkError', onNetworkError)
                client.off('gatewayConnect', onGatewayConnect)
                client.off('gatewayDisconnect', onGatewayDisconnect)
                client.off('gatewayRateLimited', onGatewayRateLimited)
                client.off('unauthorized', onUnauthorized)
                client.off('logout', onLogout)
            })
        }),
    )

    const api: APIApi = {
        client,
        authState: authState,
        tokenType: tokenType,
        login: async (id: number, password: string) => {
            configureClientAuth(client, TokenType.User)
            const credentials: LoginOptions = { id, password, clientName: `web@${process.env.APP_VERSION}` }
            await client.login(credentials)

            const token = client.rest.token
            if (!token) throw new Error('Missing auth token after user login')

            localStorage.setItem(TOKEN_KEY, token)
            localStorage.setItem(TOKEN_TYPE_KEY, TokenType.User)

            log.info('Got token!')
            log.info('Login successful')
        },
        adminLogin: async (key: CryptoKey) => {
            configureClientAuth(client, TokenType.Admin)
            const credentials: AdminAuthenticateOptions = { key }
            await client.login(credentials)

            const token = client.rest.token
            if (!token) throw new Error('Missing auth token after admin login')

            localStorage.setItem(TOKEN_KEY, token)
            localStorage.setItem(TOKEN_TYPE_KEY, TokenType.Admin)

            log.info('Got token!')
            log.info('Admin login successful')
        },
        resumeSession: async () => {
            const token = localStorage.getItem(TOKEN_KEY)
            if (!token) throw new Error('No stored token available to resume session')

            const type = getTokenType()
            const authenticator = configureClientAuth(client, type)
            authenticator.setToken(token)
            await client.resume(token)
        },
        logout: () => client.logout(),
    }

    // @ts-expect-error: Exposing to DEV
    if (import.meta.env.DEV) globalThis.$api = api

    return <APIContext.Provider value={api}>{props.children}</APIContext.Provider>
}

export default APIProvider

export const useAPI = () => {
    const context = useContext(APIContext)
    if (!context) throw new Error('useAPI must be used within a APIProvider')
    return context
}
