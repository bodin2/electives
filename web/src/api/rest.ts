/**
 * REST API client for the Electives API
 * Handles HTTP requests with authentication and error handling
 * Uses Protobuf for serialization
 */

import Logger from '@bodin2/electives-common/Logger'
import {
    APIError,
    BadRequestError,
    ConflictError,
    ForbiddenError,
    NetworkError,
    NotFoundError,
    RateLimitError,
    type RateLimitInfo,
    UnauthorizedError,
} from './types'
import { sleep } from './utils'
import type { MessageFns } from '@bodin2/electives-common/proto/api'

export interface RESTOptions {
    /** Base URL for the API */
    baseURL: string
    /**
     * Request timeout in milliseconds.
     * @default 30000
     */
    timeout?: number
    /**
     * Whether to automatically retry on rate limit
     * @default true
     */
    retryOnRateLimit?: boolean
    /** Handler for request errors */
    onError?: (error: APIError) => void
}

export interface RequestOptions<TReq = unknown, TRes = unknown> {
    /** HTTP method */
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
    /** Request body data */
    body?: TReq
    /** Protobuf encoder for request body */
    encoder?: MessageFns<TReq>
    /** Protobuf decoder for response body */
    decoder?: MessageFns<TRes>
    /** Additional headers */
    headers?: Record<string, string>
    /** Query parameters */
    query?: Record<string, string | number | boolean | undefined>
    /** Request timeout override */
    timeout?: number
    /** Override retryOnRateLimit for this request. Follows the default if not set. */
    retryOnRateLimit?: boolean
}

/** Rate limit state tracked from response headers */
export interface RateLimitState {
    /** Maximum requests allowed in the window */
    limit: number
    /** Remaining requests in the window */
    remaining: number
    /** Unix timestamp (seconds) when the limit resets */
    reset: number
}

/** Per-route rate limit bucket */
export interface RateLimitBucket {
    /** The route key (method:path) */
    route: string
    /** Rate limit state for this route */
    state: RateLimitState
}

const log = new Logger('RESTClient')

export class RESTClient {
    readonly baseURL: string
    readonly timeout: number
    readonly retryOnRateLimit: boolean

    token: string | null = null

    /** Per-route rate limit buckets */
    private readonly rateLimitBuckets = new Map<string, RateLimitState>()
    private readonly onError: (error: APIError) => void

    constructor(options: RESTOptions) {
        this.baseURL = options.baseURL.replace(/\/$/, '')
        this.timeout = options.timeout ?? 30000
        this.onError = options.onError ?? (() => {})
        this.retryOnRateLimit = options.retryOnRateLimit ?? true
    }

    getRateLimitState(method: string, path: string): RateLimitState | null {
        const routeKey = this.routeKeyFor(method, path)
        return this.rateLimitBuckets.get(routeKey) ?? null
    }

    private routeKeyFor(method: string, path: string): string {
        return `${method}:${new URL(path, this.baseURL).pathname}`
    }

    setToken(token: string | null): void {
        this.token = token
    }

    async get<T>(path: string, options?: Omit<RequestOptions<never, T>, 'method' | 'body' | 'encoder'>): Promise<T> {
        return this.request<never, T>(path, { ...options, method: 'GET' })
    }

    async post<T>(
        path: string,
        body?: unknown,
        options?: Omit<RequestOptions<unknown, T>, 'method' | 'body'>,
    ): Promise<T> {
        return this.request<unknown, T>(path, { ...options, method: 'POST', body })
    }

    async put<T>(
        path: string,
        body?: unknown,
        options?: Omit<RequestOptions<unknown, T>, 'method' | 'body'>,
    ): Promise<T> {
        return this.request<unknown, T>(path, { ...options, method: 'PUT', body })
    }

    async delete<T>(path: string, options?: Omit<RequestOptions<never, T>, 'method' | 'body' | 'encoder'>): Promise<T> {
        return this.request<never, T>(path, { ...options, method: 'DELETE' })
    }

    async patch<T>(
        path: string,
        body?: unknown,
        options?: Omit<RequestOptions<unknown, T>, 'method' | 'body'>,
    ): Promise<T> {
        return this.request<unknown, T>(path, { ...options, method: 'PATCH', body })
    }

    async request<TReq, TRes>(path: string, options: RequestOptions<TReq, TRes> = {}): Promise<TRes> {
        const {
            method = 'GET',
            body,
            encoder,
            decoder,
            headers = {},
            query,
            timeout = this.timeout,
            retryOnRateLimit = this.retryOnRateLimit,
        } = options

        // Check rate limit
        const routeKey = this.routeKeyFor(method, path)
        const bucket = this.rateLimitBuckets.get(routeKey)
        if (bucket && bucket.remaining === 0) {
            const waitTime = bucket.reset * 1000 - Date.now()
            if (waitTime > 0 && retryOnRateLimit) await sleep(waitTime)
        }

        const url = new URL(path, this.baseURL)
        if (query)
            for (const [key, value] of Object.entries(query)) {
                if (value !== undefined) {
                    url.searchParams.append(key, String(value))
                }
            }

        const requestHeaders: Record<string, string> = {
            'Content-Type': 'application/x-protobuf',
            Accept: 'application/x-protobuf',
            ...headers,
        }

        if (this.token) {
            requestHeaders.Authorization = `Bearer ${this.token}`
        }

        let requestBody: BodyInit | undefined
        if (body !== undefined && encoder) {
            const bytes = encoder.encode(body as TReq).finish()
            const buffer = new ArrayBuffer(bytes.byteLength)
            new Uint8Array(buffer).set(bytes)
            requestBody = buffer
        }

        const controller = new AbortController()
        const timeoutId = setTimeout(() => controller.abort(), timeout)

        try {
            const response = await fetch(url, {
                method,
                headers: requestHeaders,
                body: requestBody,
                signal: controller.signal,
            })

            clearTimeout(timeoutId)

            if (response.status === 429) {
                const retryAfter = this.getRetryAfterSeconds(response) * 1000
                this.markRateLimited(routeKey, retryAfter)

                if (retryOnRateLimit) {
                    log.warn(`Rate limited on ${method} ${path}, retrying after ${retryAfter} ms`)

                    if (retryAfter > 0) {
                        await sleep(retryAfter)
                        return this.request<TReq, TRes>(path, { ...options, retryOnRateLimit: false })
                    }
                }
            }

            this.updateRateLimitState(response, routeKey)

            // Handle errors
            if (!response.ok) return await this.handleErrorResponse(response)

            const contentType = response.headers.get('content-type')
            if (!contentType) return undefined as TRes

            // Handle protobuf response
            if (contentType.includes('application/x-protobuf')) {
                const buffer = await response.arrayBuffer()
                if (decoder) return decoder.decode(new Uint8Array(buffer)) as TRes

                throw new TypeError('No decoder provided for protobuf response')
            }

            throw new APIError(`Unsupported Content-Type: ${contentType}`, response.status)
        } catch (error) {
            clearTimeout(timeoutId)

            let err: APIError | undefined

            if (error instanceof APIError) {
                err = error
            } else if (error instanceof Error) {
                if (error.name === 'AbortError') {
                    err = new NetworkError(`Request timed out after ${timeout} ms`, NetworkError.Type.Timeout)
                } else if (error instanceof TypeError) {
                    err = new NetworkError(error.message, NetworkError.Type.Generic)
                }
            }

            err ??= new APIError(`Unknown error: ${String(error)}`, 0, 'UNKNOWN')
            return this.handleError(err)
        }
    }

    /**
     * Update rate limit state from response headers
     */
    private updateRateLimitState(response: Response, routeKey: string): void {
        const limit = response.headers.get('x-ratelimit-limit')
        const remaining = response.headers.get('x-ratelimit-remaining')
        const reset = response.headers.get('x-ratelimit-reset')

        if (limit !== null && remaining !== null && reset !== null) {
            const state: RateLimitState = {
                limit: Number.parseInt(limit, 10),
                remaining: Number.parseInt(remaining, 10),
                reset: Number.parseInt(reset, 10),
            }

            this.rateLimitBuckets.set(routeKey, state)
        }
    }

    private getRetryAfterSeconds(response: Response): number {
        const retryAfter = response.headers.get('retry-after')
        if (!retryAfter) return 5

        const parsed = Number.parseInt(retryAfter, 10)
        // TODO: Currently Kotlin truncates fractional seconds, so we add 1 second to be safe. We need this to be fixed in Ktor.
        // https://youtrack.jetbrains.com/issue/KTOR-9285/Server-RateLimit-Retry-After-milliseconds-is-truncated
        return Number.isNaN(parsed) ? 60 : parsed + 1
    }

    private markRateLimited(routeKey: string, retryAfter: number): void {
        const bucket = this.rateLimitBuckets.get(routeKey)
        const now = Math.floor(Date.now() / 1000)

        this.rateLimitBuckets.set(routeKey, {
            limit: bucket?.limit ?? 0,
            remaining: 0,
            reset: now + retryAfter,
        })
    }

    private async handleErrorResponse(response: Response): Promise<never> {
        const message = (await response.text()) || response.statusText

        try {
            switch (response.status) {
                case 400:
                    throw new BadRequestError(message)
                case 401:
                    throw new UnauthorizedError(message)
                case 403:
                    throw new ForbiddenError(message)
                case 404:
                    throw new NotFoundError(message)
                case 409:
                    throw new ConflictError(message)
                case 429: {
                    const retryAfter = this.getRetryAfterSeconds(response)
                    const now = Math.floor(Date.now() / 1000)
                    const rateLimitInfo: RateLimitInfo = {
                        limit: 0,
                        remaining: 0,
                        reset: now + retryAfter,
                        retryAfter,
                    }
                    throw new RateLimitError(rateLimitInfo, message)
                }
                default:
                    throw new APIError(message, response.status)
            }
        } catch (error) {
            return this.handleError(error)
        }
    }

    private handleError(error: unknown): never {
        if (error instanceof APIError) {
            this.onError(error)
        }

        throw error
    }
}
