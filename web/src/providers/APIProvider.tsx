import Logger from '@bodin2/electives-common/Logger'
import {
    type Accessor,
    createContext,
    createEffect,
    createSignal,
    on,
    type ParentComponent,
    useContext,
} from 'solid-js'
import { APIError, Client, UnauthorizedError } from '../api'
import { Route } from '../routes/__root'

export enum AuthenticationState {
    Loading = 0,
    LoggedOut = 1,
    LoggedIn = 2,
    NetworkError = 3,
}

interface APIApi {
    client: Client
    $authState: Accessor<AuthenticationState>
    $loginPromise: Accessor<Promise<void>>
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
    const [loginPromise, setLoginPromise] = createSignal<Promise<void>>(newLoginPromise())

    function newLoginPromise() {
        return new Promise<void>(resolve => {
            client.once('ready', () => {
                resolve()
            })
        })
    }

    createEffect(() => {
        log.debug('Authentication state changed to:', AuthenticationState[authState()])
    })

    createEffect(() => {
        ctx().authState.then(state => {
            log.info('Syncing router auth state:', AuthenticationState[state])
            setAuthState(state)
        })
    })

    createEffect(
        on(loginPromise, () => {
            if (client.isLoggedIn()) {
                client.connectGateway()
            } else {
                const token = localStorage.getItem(TOKEN_KEY)
                if (!token) {
                    setAuthState(AuthenticationState.LoggedOut)
                }
            }

            client.on('ready', user => {
                log.info('Logged in as:', user)
                setAuthState(AuthenticationState.LoggedIn)
            })

            client.on('networkError', err => {
                log.error('Network error occurred', err)
                if (authState() !== AuthenticationState.LoggedOut) setAuthState(AuthenticationState.NetworkError)
            })

            client.on('gatewayConnect', () => {
                log.info('Connected to gateway')
            })

            client.on('gatewayDisconnect', () => {
                log.warn('Disconnected from gateway')
            })

            client.on('gatewayRateLimited', retryAfter => {
                log.warn('Gateway rate limited, retrying after:', retryAfter, 'ms')
            })

            client.once('unauthorized', error => {
                if (authState() === AuthenticationState.LoggedOut) {
                    log.warn('Received unauthorized event while logged out, likely a bad session.')
                    api.logout()
                    return
                }

                client.rest.get('/users/@me').catch(e => {
                    if (e instanceof UnauthorizedError) {
                        log.warn('Unauthorized, logging out:', error.message)
                        return client.logout()
                    }
                })
            })

            client.on('logout', () => {
                client.destroy()
                localStorage.removeItem(TOKEN_KEY)
                setAuthState(AuthenticationState.LoggedOut)
                setLoginPromise(newLoginPromise())
            })

            // TODO: onCleanup
        }),
    )

    const api: APIApi = {
        client,
        $authState: authState,
        $loginPromise: loginPromise,
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
