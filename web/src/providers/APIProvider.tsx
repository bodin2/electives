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
import { Client, UnauthorizedError } from '../api'

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

export const initAuth = (client: Client): Promise<boolean> => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) return Promise.resolve(false)

    log.info('Got token!')

    return client.login(token).then(
        () => true,
        e => {
            log.error('Failed to login with stored token:', e)
            return false
        },
    )
}

const APIProvider: ParentComponent<{ client: Client }> = props => {
    const client = props.client

    const initialState = client.isLoggedIn() ? AuthenticationState.LoggedIn : AuthenticationState.Loading
    const [authState, setAuthState] = createSignal(initialState)
    const [loginPromise, setLoginPromise] = createSignal<Promise<void>>(
        initialState === AuthenticationState.LoggedIn ? Promise.resolve() : newLoginPromise(),
    )

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
