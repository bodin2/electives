import Logger from '@bodin2/electives-common/Logger'
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
import { APIError, Client, type ClientEventMap, UnauthorizedError } from '../api'
import { NetworkError } from '../api/types'
import { Route } from '../routes/__root'

export enum AuthenticationState {
    Loading = 0,
    LoggedOut = 1,
    LoggedIn = 2,
    NetworkError = 3,
}

interface APIApi {
    client: Client
    authState: Accessor<AuthenticationState>
    login(id: number, password: string): Promise<void>
    logout: () => Promise<void>
}

const TOKEN_KEY = 'auth_token'
const APIContext = createContext<APIApi>()
const log = new Logger('APIProvider')

export const createClient = () =>
    new Client({
        baseURL: process.env.API_BASE_URL || 'http://localhost:8080',
        autoConnect: true,
        gateway: {
            maxReconnectAttempts: 3,
            reconnectDelay: 5000,
        },
    })

export const initAuth = (client: Client): Promise<AuthenticationState> => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) return Promise.resolve(AuthenticationState.LoggedOut)

    log.info('Got token!')

    return client.login(token).then(
        () => AuthenticationState.LoggedIn,
        e => {
            log.error('Failed to login with stored token:', e)

            if (e instanceof APIError && !(e instanceof NetworkError)) {
                return AuthenticationState.LoggedOut
            }

            return AuthenticationState.NetworkError
        },
    )
}

const APIProvider: ParentComponent<{ client: Client }> = props => {
    const client = props.client
    const ctx = Route.useRouteContext()

    const [authState, setAuthState] = createSignal(AuthenticationState.Loading)
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

    createEffect(
        on(updater, () => {
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
                log.warn('Disconnected from gateway:', reason)
            }

            const onGatewayRateLimited = (retryAfter: ClientEventMap['gatewayRateLimited']) => {
                log.warn('Gateway rate limited, retrying after:', retryAfter, 'ms')
            }

            let loggingOut = false
            const onUnauthorized = (error: ClientEventMap['unauthorized']) => {
                if (authState() === AuthenticationState.LoggedOut || loggingOut) {
                    log.warn('Received unauthorized event while logged out, likely a bad session.')
                    return
                }

                loggingOut = true
                client.rest.get('/users/@me').catch(e => {
                    if (e instanceof UnauthorizedError) {
                        log.warn('Unauthorized, logging out:', error.message)
                        return client.logout()
                    }
                    loggingOut = false
                })
            }

            const onLogout = () => {
                client.destroy()

                localStorage.removeItem(TOKEN_KEY)
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
            client.once('unauthorized', onUnauthorized)
            client.on('logout', onLogout)

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
            const token = await client.authenticate({ id, password, clientName: `web@${process.env.APP_VERSION}` })
            localStorage.setItem(TOKEN_KEY, token)
            log.info('Got token!')
            await client.login(token)
            log.info('Login successful')
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
    if (!context) throw new Error('useAPI must be used within a ClientProvider')
    return context
}
