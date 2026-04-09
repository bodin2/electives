import mitt from 'mitt'
import { Cache } from './cache'
import { type Gateway, type GatewayEventMap, GatewayStatus } from './gateway'
import { ElectiveManager } from './managers/ElectiveManager'
import { SelectionManager } from './managers/SelectionManager'
import { SubjectManager } from './managers/SubjectManager'
import { TeamManager } from './managers/TeamManager'
import { UserManager } from './managers/UserManager'
import { NetworkError, UnauthorizedError } from './types'
import type { Emitter, Handler } from 'mitt'
import type { Authenticator } from './auth'
import type { RESTClient } from './rest'
import type { User } from './structures'

export type ClientEventMap = {
    /** Fired when the client is ready (logged in) */
    ready: User
    /** Fired when the client logs out */
    logout: undefined
    /** Fired when the gateway connects */
    gatewayConnect: GatewayEventMap['connect']
    /** Fired when the gateway disconnects */
    gatewayDisconnect: GatewayEventMap['disconnect']
    /** Fired when the gateway is rate limited */
    gatewayRateLimited: GatewayEventMap['rateLimited']
    /** Fired when a subscribed subject enrollment count updates */
    subjectEnrollmentUpdate: GatewayEventMap['subjectEnrollmentUpdate']
    /** Fired when a bulk subject enrollment count update occurs */
    bulkSubjectEnrollmentUpdate: GatewayEventMap['bulkSubjectEnrollmentUpdate']
    /** Fired on any error */
    error: Error
    /** Fired when an unauthorized error occurs (HTTP 401) */
    unauthorized: UnauthorizedError
    /** Fired when a network error occurs */
    networkError: NetworkError
}

export type ClientEventNames = keyof ClientEventMap
export type ClientEventHandler<K extends ClientEventNames> = Handler<ClientEventMap[K]>

export interface ClientOptions<TCredentials> {
    rest: RESTClient
    gateway: Gateway
    authenticator: Authenticator<TCredentials>
    /**
     * Whether to automatically connect to the gateway on login
     * @default true
     */
    autoConnect?: boolean
    /**
     * Cache TTL in milliseconds
     * @default 300000 // 5 minutes
     */
    cacheTTL?: number
}

/**
 * The main client for interacting with the Electives API
 *
 * @example
 * ```ts
 * const rest = new RESTClient({ baseURL: 'https://api.example.com' })
 * const gateway = new Gateway({ url: 'wss://api.example.com/notifications' })
 * const authenticator = new UserAuthenticator(rest)
 *
 * const client = new Client({ rest, gateway, authenticator })
 *
 * await client.login({ id: 12345, password: 'secret' })
 *
 * // Fetch electives
 * const electives = await client.electives.fetchAll()
 *
 * // Subscribe to real-time updates
 * client.gateway.subscribeToElective(1, [1, 2, 3])
 *
 * client.on('subjectEnrollmentUpdate', ({ electiveId, subjectId, enrolledCount }) => {
 *   console.log(`Subject ${subjectId} now has ${enrolledCount} enrollments`)
 * })
 * ```
 */
export class Client<TCredentials> {
    /** The REST client for HTTP requests */
    readonly rest: RESTClient

    /** The WebSocket gateway for real-time updates */
    readonly gateway: Gateway

    /** Manager for users */
    readonly users: UserManager

    /** Manager for electives */
    readonly electives: ElectiveManager

    /** Manager for subjects */
    readonly subjects: SubjectManager

    /** Manager for student selections */
    readonly selections: SelectionManager

    /** Manager for teams */
    readonly teams: TeamManager

    /** Whether to auto-connect to the gateway on login */
    readonly autoConnect: boolean

    /** The currently authenticated user */
    user: User | null = null

    private authenticator: Authenticator<unknown>
    private readonly emitter: Emitter<ClientEventMap> = mitt<ClientEventMap>()

    constructor(options: ClientOptions<TCredentials>) {
        const { rest, gateway, authenticator, autoConnect = true, cacheTTL = 5 * 60 * 1000 } = options

        this.rest = rest
        this.gateway = gateway
        this.authenticator = authenticator as Authenticator<unknown>
        this.autoConnect = autoConnect

        const cacheOpts = { ttl: cacheTTL }
        const infiniteCacheOpts = { ttl: Number.POSITIVE_INFINITY }

        this.users = new UserManager(this.rest, new Cache(cacheOpts))
        this.subjects = new SubjectManager(this.rest, new Cache(infiniteCacheOpts), new Cache(cacheOpts))
        this.electives = new ElectiveManager(this.rest, new Cache(infiniteCacheOpts), this.subjects)
        this.selections = new SelectionManager(this.rest, new Cache(cacheOpts), () => {
            if (!this.user) throw new Error('Not logged in')
            return this.user.id
        })
        this.teams = new TeamManager(this.rest, new Cache(cacheOpts))

        this.rest.onError = err => this.handleError(err)
        this.setupGatewayListeners()
    }

    /**
     * Authenticate, fetch the current user, and optionally connect the gateway.
     */
    async login(credentials: TCredentials): Promise<void> {
        const token = await this.authenticator.login(credentials)
        this.rest.token = token

        const synthetic = this.authenticator.syntheticUser()
        this.user = synthetic ?? (await this.users.fetch('@me'))

        if (this.autoConnect) {
            this.gateway.connect(this.authenticator)
        }

        this.emitter.emit('ready', this.user)
    }

    /**
     * Logout, disconnect the gateway, and clear all caches.
     */
    async logout(): Promise<void> {
        try {
            await this.authenticator.logout()
        } finally {
            this.authenticator.setToken(null)
            this.rest.token = null
            this.user = null
            this.gateway.disconnect()
            this.clearCaches()
            this.emitter.emit('logout')
        }
    }

    /**
     * Check if the client is logged in
     */
    isLoggedIn(): boolean {
        return this.rest.token !== null && this.user !== null
    }

    /**
     * Check if there is an existing valid session
     */
    hasSession(): Promise<boolean> {
        return this.authenticator.hasSession()
    }

    /**
     * Restore a session from a previously obtained token without re-authenticating.
     * Fetches the current user and optionally connects the gateway.
     */
    async resume(token: string): Promise<void> {
        this.authenticator.setToken(token)
        this.rest.token = token

        const synthetic = this.authenticator.syntheticUser()
        this.user = synthetic ?? (await this.users.fetch('@me'))

        if (this.autoConnect) {
            this.gateway.connect(this.authenticator)
        }

        this.emitter.emit('ready', this.user)
    }

    /**
     * Connect to the gateway manually
     */
    connectGateway(): void {
        if (!this.rest.token) {
            throw new Error('Cannot connect to gateway without authentication')
        }
        this.gateway.connect(this.authenticator)
    }

    /**
     * Disconnect from the gateway
     */
    disconnectGateway(): void {
        this.gateway.disconnect()
    }

    /**
     * Check if connected to the gateway
     */
    isGatewayConnected(): boolean {
        return this.gateway.status === GatewayStatus.READY
    }

    /**
     * Replace the active authenticator (e.g. user <-> admin).
     * Disconnects the gateway so future connections use the new authenticator.
     */
    setAuthenticator<TNewCredentials>(authenticator: Authenticator<TNewCredentials>): Client<TNewCredentials> {
        this.gateway.disconnect()
        this.authenticator = authenticator as Authenticator<unknown>
        return this as unknown as Client<TNewCredentials>
    }

    /**
     * Update the gateway URL (e.g. `/notifications` <-> `/admin/notifications`).
     */
    setGatewayURL(url: string): void {
        this.gateway.setURL(url)
    }

    /**
     * Clear all caches
     */
    clearCaches(): void {
        this.users.clearCache()
        this.subjects.clearCache()
        this.electives.clearCache()
        this.selections.clearCache()
        this.teams.clearCache()
    }

    on<K extends ClientEventNames>(event: K, handler: ClientEventHandler<K>): this {
        this.emitter.on(event, handler)
        return this
    }

    once<K extends ClientEventNames>(event: K, handler: ClientEventHandler<K>): this {
        const wrapper = ((arg: ClientEventMap[K]) => {
            this.off(event, wrapper as ClientEventHandler<K>)
            handler(arg)
        }) as ClientEventHandler<K>
        return this.on(event, wrapper)
    }

    off<K extends ClientEventNames>(event: K, handler: ClientEventHandler<K>): this {
        this.emitter.off(event, handler)
        return this
    }

    removeAllListeners(event?: ClientEventNames): this {
        if (event) {
            this.emitter.all.delete(event)
        } else {
            this.emitter.all.clear()
        }
        return this
    }

    /**
     * Destroy the client, disconnecting and cleaning up resources
     */
    destroy(): void {
        this.gateway.disconnect()
        this.clearCaches()
        this.removeAllListeners()
        this.rest.token = null
        this.user = null
    }

    private setupGatewayListeners(): void {
        this.gateway.on('connect', () => {
            this.emitter.emit('gatewayConnect')
        })

        this.gateway.on('disconnect', reason => {
            this.emitter.emit('gatewayDisconnect', reason)
        })

        this.gateway.on('rateLimited', retryAfter => {
            this.emitter.emit('gatewayRateLimited', retryAfter)
        })

        this.gateway.on('error', error => {
            this.emitter.emit('error', error)
        })

        this.gateway.on('subjectEnrollmentUpdate', update => {
            this.emitter.emit('subjectEnrollmentUpdate', {
                electiveId: update.electiveId,
                subjectId: update.subjectId,
                enrolledCount: update.enrolledCount,
            })
        })

        this.gateway.on('bulkSubjectEnrollmentUpdate', update => {
            this.emitter.emit('bulkSubjectEnrollmentUpdate', {
                electiveId: update.electiveId,
                subjectEnrolledCounts: update.subjectEnrolledCounts,
            })
        })
    }

    private handleError(error: Error): void {
        if (error instanceof UnauthorizedError) {
            this.emitter.emit('unauthorized', error)
        }

        if (error instanceof NetworkError) {
            this.emitter.emit('networkError', error)
        }

        this.emitter.emit('error', error)
    }
}
