import mitt, { type Emitter, type Handler } from 'mitt'
import { Cache } from './cache'
import { Gateway, GatewayStatus } from './gateway'
import { ElectiveManager, SelectionManager, SubjectManager, UserManager } from './managers'
import { RESTClient } from './rest'
import {
    AuthenticateRequestCodec,
    type AuthenticateResponse,
    AuthenticateResponseCodec,
    type ClientOptions,
    type LoginOptions,
    NetworkError,
    UnauthorizedError,
} from './types'
import type { User } from './structures'

export type SubjectEnrollmentUpdateEvent = {
    electiveId: number
    subjectId: number
    enrolledCount: number
}

export type ClientEventMap = {
    /** Fired when the client is ready (logged in) */
    ready: User
    /** Fired when the client logs out */
    logout: undefined
    /** Fired when the gateway connects */
    gatewayConnect: undefined
    /** Fired when the gateway disconnects */
    gatewayDisconnect: string
    /** Fired when the gateway is rate limited */
    gatewayRateLimited: number
    /** Fired when a subject enrollment count updates */
    subjectEnrollmentUpdate: SubjectEnrollmentUpdateEvent
    /** Fired on any error */
    error: Error
    /** Fired when an unauthorized error occurs (HTTP 401) */
    unauthorized: UnauthorizedError
    /** Fired when a network error occurs */
    networkError: NetworkError
}

export type ClientEventNames = keyof ClientEventMap
export type ClientEventHandler<K extends ClientEventNames> = Handler<ClientEventMap[K]>

/**
 * The main client for interacting with the Electives API
 *
 * @example
 * ```ts
 * const client = new Client({
 *   baseURL: "https://api.example.com",
 *   wsURL: "wss://api.example.com/notifications",
 * });
 *
 * await client.login({ id: 12345, password: "secret" });
 *
 * // Fetch electives
 * const electives = await client.electives.fetchAll();
 *
 * // Subscribe to real-time updates
 * client.gateway.subscribeToElective(1, [1, 2, 3]);
 *
 * client.on("subjectEnrollmentUpdate", ({ electiveId, subjectId, enrolledCount }) => {
 *   console.log(`Subject ${subjectId} now has ${enrolledCount} enrollments`);
 * });
 * ```
 */
export class Client {
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

    /** Whether to auto-connect to the gateway on login */
    readonly autoConnect: boolean

    /** The currently authenticated user */
    user: User | null = null

    private readonly emitter: Emitter<ClientEventMap> = mitt<ClientEventMap>()

    constructor(options: ClientOptions) {
        const { baseURL, notificationsURL: wsURL, timeout, cacheTTL = 5 * 60 * 1000, autoConnect = true } = options

        this.autoConnect = autoConnect

        this.rest = new RESTClient({
            baseURL,
            timeout,
            onError: err => this.handleError(err),
        })

        const gatewayURL = wsURL ?? `${baseURL.replace(/^http/, 'ws')}/notifications`
        this.gateway = new Gateway(Object.assign({ url: gatewayURL }, options.gateway))
        this.setupGatewayListeners()

        const cacheOpts = { ttl: cacheTTL }
        const infiniteCacheOpts = { ttl: Number.POSITIVE_INFINITY }

        this.users = new UserManager(this.rest, new Cache(cacheOpts))
        this.subjects = new SubjectManager(this.rest, new Cache(infiniteCacheOpts), new Cache(cacheOpts))
        this.electives = new ElectiveManager(this.rest, new Cache(infiniteCacheOpts), this.subjects)
        this.selections = new SelectionManager(this, new Cache(cacheOpts))
    }

    /**
     * Authenticate to the API
     * @param options Login credentials
     * @returns The authentication token
     */
    async authenticate(options: LoginOptions): Promise<string> {
        const { id, password, clientName = 'API Client' } = options

        const response = await this.rest.post<AuthenticateResponse>(
            '/auth',
            { id, password, clientName },
            {
                encoder: AuthenticateRequestCodec,
                decoder: AuthenticateResponseCodec,
            },
        )

        return response.token
    }

    async login(token?: string | undefined): Promise<void> {
        const tokenToUse = token ?? this.rest.token
        if (!tokenToUse) {
            throw new Error('Cannot login without authentication token')
        }

        this.rest.token = tokenToUse
        this.user = await this.users.fetch('@me')

        if (this.autoConnect) {
            this.gateway.connect(tokenToUse)
        }

        this.emitter.emit('ready', this.user)
    }

    /**
     * Logout from the API
     */
    async logout(): Promise<void> {
        try {
            await this.rest.post('/logout')
        } finally {
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
     * Get the authentication token
     */
    get token(): string | null {
        return this.rest.token
    }

    /**
     * Set the authentication token manually (for restoring sessions)
     */
    setToken(token: string): void {
        this.rest.token = token
    }

    /**
     * Connect to the gateway manually
     */
    connectGateway(): void {
        const token = this.rest.token
        if (!token) {
            throw new Error('Cannot connect to gateway without authentication')
        }
        this.gateway.connect(token)
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
     * Clear all caches
     */
    clearCaches(): void {
        this.users.cache.clear()
        this.electives.cache.clear()
        this.subjects.cache.clear()
        this.selections.cache.clear()
    }

    /**
     * Add an event listener
     */
    on<K extends ClientEventNames>(event: K, handler: ClientEventHandler<K>): this {
        this.emitter.on(event, handler)
        return this
    }

    /**
     * Add a one-time event listener
     */
    once<K extends ClientEventNames>(event: K, handler: ClientEventHandler<K>): this {
        const wrapper = ((arg: ClientEventMap[K]) => {
            this.off(event, wrapper as ClientEventHandler<K>)
            handler(arg)
        }) as ClientEventHandler<K>
        return this.on(event, wrapper)
    }

    /**
     * Remove an event listener
     */
    off<K extends ClientEventNames>(event: K, handler: ClientEventHandler<K>): this {
        this.emitter.off(event, handler)
        return this
    }

    /**
     * Remove all listeners for an event (or all events if no event specified)
     */
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
        this.gateway.token = null
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
            for (const [subjectIdStr, count] of Object.entries(update.subjectEnrolledCounts)) {
                const subjectId = Number.parseInt(subjectIdStr, 10)
                this.emitter.emit('subjectEnrollmentUpdate', {
                    electiveId: update.electiveId,
                    subjectId: subjectId,
                    enrolledCount: count,
                })
            }
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
