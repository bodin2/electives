import Logger from '@bodin2/electives-common/Logger'
import { useRouteContext } from '@tanstack/solid-router'
import {
    type Accessor,
    createContext,
    createRenderEffect,
    createSignal,
    on,
    onCleanup,
    type ParentComponent,
    useContext,
} from 'solid-js'
import {
    APIError,
    type Authenticator,
    Client,
    type ClientEventMap,
    Gateway,
    type LoginOptions,
    RESTClient,
    UnauthorizedError,
    UserAuthenticator,
} from '~/api'
import { GatewayEndpoints } from '~/api/gateway'
import { NetworkError } from '~/api/types'
import { API_BASE_URL, API_CLIENT_NAME } from '~/constants'
import { queryClient } from '~/queries/queryClient'
import { nonNull } from '~/utils'

export enum AuthenticationState {
    Loading = 0,
    LoggedOut = 1,
    LoggedIn = 2,
    NetworkError = 3,
}

interface APIApi {
    client: Client<unknown>
    authState: Accessor<AuthenticationState>
    login(id: number, password: string): Promise<void>
    resumeSession(): Promise<void>
    logout: () => Promise<void>
}

const TOKEN_KEY = 'auth_token'
const APIContext = createContext<APIApi>()
const log = new Logger('APIProvider')

type APIClient = Client<unknown>
const gatewayURLFromBaseURL = (baseURL: string): string => {
    const url = new URL(baseURL)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    url.pathname = GatewayEndpoints.Notifications
    url.search = ''
    url.hash = ''
    return url.toString()
}

const configureClientAuth = (client: APIClient): Authenticator<unknown> => {
    const authenticator = new UserAuthenticator(client.rest) as Authenticator<unknown>
    client.setAuthenticator(authenticator)
    client.setGatewayURL(gatewayURLFromBaseURL(client.rest.baseURL))
    return authenticator
}

export const createClient = () => {
    const baseURL = API_BASE_URL
    const rest = new RESTClient({ baseURL })
    const gateway = new Gateway({
        url: gatewayURLFromBaseURL(baseURL),
        maxReconnectAttempts: 3,
        reconnectDelay: 5000,
    })

    latestClient = new Client({
        rest,
        gateway,
        authenticator: new UserAuthenticator(rest) as Authenticator<unknown>,
        autoConnect: true,
    })

    return latestClient
}

export let latestClient: Client<unknown> | null = null

export const initAuth = async (client: APIClient): Promise<AuthenticationState> => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) return AuthenticationState.LoggedOut

    log.info('Got token!')

    const authenticator = configureClientAuth(client)
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

    createRenderEffect(() => {
        log.debug('Authentication state changed to:', AuthenticationState[authState()])
    })

    createRenderEffect(() => {
        ctx().authState.then(state => {
            log.debug('Syncing router auth state:', AuthenticationState[state])
            setAuthState(state)
        })
    })

    function checkSession(error: Error) {
        client.hasSession().then(hasSession => {
            if (!hasSession) {
                log.warn('Unauthorized, logging out:', error.message)
                return client.logout()
            }
        })
    }

    createRenderEffect(
        on(authState, state => {
            if (state === AuthenticationState.NetworkError) return

            const onReady = (user: ClientEventMap['ready']) => {
                log.info('Logged in as:', user)
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
                if (authState() === AuthenticationState.LoggedOut) {
                    log.warn('Received unauthorized event while logged out, likely a bad session.')
                    return
                }

                checkSession(error)
            }

            const onLogout = () => {
                localStorage.removeItem(TOKEN_KEY)
                setAuthState(AuthenticationState.LoggedOut)
                queryClient.clear()

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
        login: async (id: number, password: string) => {
            configureClientAuth(client)
            const credentials: LoginOptions = { id, password, clientName: API_CLIENT_NAME }
            await client.login(credentials)

            const token = client.rest.token
            if (!token) throw new Error('Missing auth token after user login')

            localStorage.setItem(TOKEN_KEY, token)

            log.info('Got token!')
            log.info('Login successful')
        },
        resumeSession: async () => {
            const token = localStorage.getItem(TOKEN_KEY)
            if (!token) throw new Error('No stored token available to resume session')

            setAuthState(AuthenticationState.Loading)

            const authenticator = configureClientAuth(client)
            authenticator.setToken(token)

            try {
                await client.resume(token)
            } catch (e) {
                if (e instanceof UnauthorizedError) {
                    log.warn('Failed to resume session with stored token, logging out:', e)
                    await client.logout().catch(() => null)
                    setAuthState(AuthenticationState.LoggedOut)
                }

                throw e
            }
        },
        logout: () => client.logout(),
    }

    // @ts-expect-error: Exposing to DEV
    if (import.meta.env.DEV) globalThis.$api = api

    return <APIContext.Provider value={api}>{props.children}</APIContext.Provider>
}

export default APIProvider

export const useAPI = () => nonNull(useContext(APIContext), 'useAPI must be used within a APIProvider')
