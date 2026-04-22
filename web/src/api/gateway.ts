import mitt, { type Emitter, type Handler } from 'mitt'
import {
    type BulkSubjectEnrollmentUpdate,
    NotificationEnvelope,
    type SubjectEnrollmentUpdate,
    type SubjectEnrollmentUpdateSubscription,
    type SubjectEnrollmentUpdateSubscriptionRequest,
} from './types'
import { encodeToBytes } from './utils'

export type GatewayEventMap = {
    /** Fired when the gateway connects */
    connect: undefined
    /** Fired when the gateway disconnects */
    disconnect: { code: number; reason: string }
    /** Fired when the gateway encounters an error */
    error: Error
    /** Fired when the gateway is rate limited (HTTP 429) */
    rateLimited: number
    /** Fired when a subject enrollment update is received */
    subjectEnrollmentUpdate: SubjectEnrollmentUpdate
    /** Fired when a bulk subject enrollment update is received */
    bulkSubjectEnrollmentUpdate: BulkSubjectEnrollmentUpdate
    /** Fired when the server acknowledges a message */
    acknowledged: number
    /** Fired on any raw message (for debugging) */
    raw: NotificationEnvelope
}

export type GatewayEventNames = keyof GatewayEventMap
export type GatewayEventHandler<K extends GatewayEventNames> = Handler<GatewayEventMap[K]>

export interface GatewayAuthenticator {
    identify(gateway: Gateway): void | Promise<void>
    refresh(): GatewayAuthenticator
}

export interface GatewayOptions {
    /** WebSocket URL for the gateway */
    url: string
    /** Whether to automatically reconnect on disconnection */
    autoReconnect?: boolean
    /**
     * Maximum number of reconnect attempts
     *
     * @default 5
     */
    maxReconnectAttempts?: number
    /**
     * Reconnect delay in ms
     *
     * @default 1000
     */
    reconnectDelay?: number
    /**
     * Whether to automatically retry on rate limit (HTTP 429)
     *
     * @default true
     */
    retryOnRateLimit?: boolean
}

export enum GatewayStatus {
    DISCONNECTED = 0,
    CONNECTING = 1,
    CONNECTED = 2,
    IDENTIFYING = 3,
    READY = 4,
    RECONNECTING = 5,
}

export class Gateway {
    private ws: WebSocket | null = null
    private messageId = 0
    private reconnectAttempts = 0
    private reconnectTimeout: ReturnType<typeof setTimeout> | null = null
    private currentAuthenticator: GatewayAuthenticator | null = null
    private readonly emitter: Emitter<GatewayEventMap> = mitt<GatewayEventMap>()

    url: string
    readonly autoReconnect: boolean
    readonly maxReconnectAttempts: number
    readonly reconnectDelay: number
    readonly retryOnRateLimit: boolean

    status: GatewayStatus = GatewayStatus.DISCONNECTED

    constructor(options: GatewayOptions) {
        this.url = options.url
        this.autoReconnect = options.autoReconnect ?? true
        this.maxReconnectAttempts = options.maxReconnectAttempts ?? 5
        this.reconnectDelay = options.reconnectDelay ?? 1000
        this.retryOnRateLimit = options.retryOnRateLimit ?? true
    }

    setURL(url: string): void {
        if (this.url === url) return

        const activeAuthenticator = this.currentAuthenticator
        this.url = url

        if (!activeAuthenticator) return

        this.disconnect()
        this.connect(activeAuthenticator)
    }

    /**
     * Connect to the gateway
     */
    connect(authenticator: GatewayAuthenticator): void {
        if (this.ws) {
            this.disconnect()
        }

        this.currentAuthenticator = authenticator
        this.status = GatewayStatus.CONNECTING

        try {
            this.ws = new WebSocket(this.url)
            this.ws.binaryType = 'arraybuffer'
            this.setupEventHandlers(authenticator)
        } catch (error) {
            this.status = GatewayStatus.DISCONNECTED
            this.emitter.emit('error', error instanceof Error ? error : new Error(String(error)))
        }
    }

    /**
     * Disconnect from the gateway
     */
    disconnect(): void {
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout)
            this.reconnectTimeout = null
        }

        if (this.ws) {
            if (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING) {
                this.ws.close(1000, 'Client disconnect')
            }
            this.ws = null
        }

        this.status = GatewayStatus.DISCONNECTED
        this.reconnectAttempts = 0
        this.currentAuthenticator = null
    }

    /**
     * Subscribe to subject enrollment updates
     */
    subscribe(subscriptions: Record<number, SubjectEnrollmentUpdateSubscription>): void {
        const request: SubjectEnrollmentUpdateSubscriptionRequest = { subscriptions }
        this.send({
            messageId: this.nextMessageId(),
            subjectEnrollmentUpdateSubscriptionRequest: request,
        })
    }

    /**
     * Subscribe to updates for specific subjects in an elective
     */
    subscribeToElective(electiveId: number, subjectIds: number[]): void {
        this.subscribe({ [electiveId]: { subjectIds } })
    }

    /**
     * Send a raw envelope over the WebSocket.
     * Authenticators use this to send identify envelopes on connect.
     */
    send(envelope: NotificationEnvelope): void {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            throw new Error('WebSocket is not connected')
        }
        const bytes = NotificationEnvelope.encode(envelope).finish()
        this.ws.send(bytes)
    }

    nextMessageId(): number {
        return ++this.messageId
    }

    isConnected(): boolean {
        return this.status === GatewayStatus.READY
    }

    on<K extends GatewayEventNames>(event: K, handler: GatewayEventHandler<K>): this {
        this.emitter.on(event, handler)
        return this
    }

    once<K extends GatewayEventNames>(event: K, handler: GatewayEventHandler<K>): this {
        const wrapper = ((arg: GatewayEventMap[K]) => {
            this.off(event, wrapper as GatewayEventHandler<K>)
            handler(arg)
        }) as GatewayEventHandler<K>
        return this.on(event, wrapper)
    }

    off<K extends GatewayEventNames>(event: K, handler: GatewayEventHandler<K>): this {
        this.emitter.off(event, handler)
        return this
    }

    removeAllListeners(event?: GatewayEventNames): this {
        if (event) {
            this.emitter.all.delete(event)
        } else {
            this.emitter.all.clear()
        }
        return this
    }

    private setupEventHandlers(authenticator: GatewayAuthenticator): void {
        const ws = this.ws
        if (!ws) return

        ws.onopen = () => {
            if (this.ws !== ws) return

            this.status = GatewayStatus.IDENTIFYING
            this.reconnectAttempts = 0

            Promise.resolve(authenticator.identify(this))
                .then(() => {
                    if (this.ws !== ws) return
                    this.status = GatewayStatus.READY
                    this.emitter.emit('connect')
                })
                .catch(error => {
                    if (this.ws !== ws) return
                    this.emitter.emit('error', error instanceof Error ? error : new Error(String(error)))
                    this.disconnect()
                })
        }

        ws.onclose = event => {
            if (this.ws !== ws) return

            const reason = event.reason || 'Connection closed'
            this.status = GatewayStatus.DISCONNECTED

            if (event.code === 4029 || event.code === 4008) {
                const retryAfter = this.parseRetryAfterFromReason(reason)
                this.emitter.emit('rateLimited', retryAfter)

                if (this.retryOnRateLimit) {
                    this.scheduleReconnect(retryAfter)
                    this.emitter.emit('disconnect', { code: event.code, reason })
                    return
                }
            }

            switch (event.code) {
                case 1000:
                case 1001:
                    break
                case 1002:
                    this.emitter.emit('error', new Error(`Client violated server protocol: [${event.code}] ${reason}`))
                    break
                default:
                    this.emitter.emit('error', new Error(`WebSocket closed unexpectedly: [${event.code}] ${reason}`))
                    break
            }

            this.emitter.emit('disconnect', { code: event.code, reason })

            if (this.autoReconnect && this.reconnectAttempts < this.maxReconnectAttempts) {
                this.scheduleReconnect()
            }
        }

        ws.onerror = () => {
            if (this.ws !== ws) return
            this.emitter.emit('error', new Error('WebSocket error'))
        }

        ws.onmessage = event => {
            if (this.ws !== ws) return
            this.handleMessage(event.data).catch(error => {
                this.emitter.emit(
                    'error',
                    error instanceof Error ? error : new Error(`Failed to handle message: ${String(error)}`),
                )
            })
        }
    }

    private async handleMessage(data: string | ArrayBuffer | Blob): Promise<void> {
        let bytes: Uint8Array
        if (data instanceof Blob) {
            bytes = new Uint8Array(await data.arrayBuffer())
        } else if (data instanceof ArrayBuffer) {
            bytes = new Uint8Array(data)
        } else {
            bytes = encodeToBytes(data)
        }

        const message = NotificationEnvelope.decode(bytes)
        this.emitter.emit('raw', message)

        if (message.subjectEnrollmentUpdate) {
            this.emitter.emit('subjectEnrollmentUpdate', message.subjectEnrollmentUpdate)
        } else if (message.bulkSubjectEnrollmentUpdate) {
            this.emitter.emit('bulkSubjectEnrollmentUpdate', message.bulkSubjectEnrollmentUpdate)
        } else if (message.messageId !== undefined) {
            this.emitter.emit('acknowledged', message.messageId)
        }
    }

    private parseRetryAfterFromReason(reason: string): number {
        const match = reason.match(/retry-after:\s*(\d+)/i)
        if (match) return Number.parseInt(match[1], 10) * 1000
        return 5000
    }

    private scheduleReconnect(customDelay?: number): void {
        if (this.ws?.readyState === WebSocket.OPEN || this.ws?.readyState === WebSocket.CONNECTING) {
            console.warn('WebSocket is already connected or connecting; aborting reconnect attempt')
            return
        }

        if (this.reconnectTimeout) clearTimeout(this.reconnectTimeout)

        this.reconnectAttempts++
        this.status = GatewayStatus.RECONNECTING

        const delay = customDelay ?? this.reconnectDelay * 2 ** (this.reconnectAttempts - 1)

        this.reconnectTimeout = setTimeout(() => {
            const auth = this.currentAuthenticator?.refresh() ?? this.currentAuthenticator
            if (auth) this.connect(auth)
        }, delay)
    }
}

export const GatewayEndpoints = {
    AdminNotifications: '/notifications',
    Notifications: '/notifications',
} as const
