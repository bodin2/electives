import {
    AdminService_AddUserRequest as AdminAddUserRequest,
    AdminService_BulkAddUsersRequest as AdminBulkAddUsersRequest,
    AdminService_BulkDeleteUsersRequest as AdminBulkDeleteUsersRequest,
    AdminService_ChallengeResponse as AdminChallengeResponse,
    AdminService_EnrollmentPatch as AdminEnrollmentPatch,
    AdminService_GroupPatch as AdminGroupPatch,
    AdminService_ListGroupsResponse as AdminListGroupsResponse,
    AdminService_ListUsersResponse as AdminListUsersResponse,
    AdminService_SetEnrollmentSubjectsRequest as AdminSetEnrollmentSubjectsRequest,
    AdminService_SetStudentSelectionsRequest as AdminSetStudentSelectionsRequest,
    AdminService_SubjectPatch as AdminSubjectPatch,
    AdminService_UserPatch as AdminUserPatch,
    AuthService_AuthenticateRequest as AuthenticateRequest,
    AuthService_AuthenticateResponse as AuthenticateResponse,
    NotificationsService_BulkSubjectEnrollmentUpdate as BulkSubjectEnrollmentUpdate,
    EnrollmentsService_ListResponse as ListEnrollmentsResponse,
    EnrollmentsService_ListSubjectMembersResponse as ListSubjectMembersResponse,
    EnrollmentsService_ListSubjectsResponse as ListSubjectsResponse,
    type MessageFns,
    NotificationsService_Envelope as NotificationEnvelope,
    NotificationsService_Identify as NotificationIdentify,
    Enrollment as RawEnrollment,
    Group as RawGroup,
    Subject as RawSubject,
    User as RawUser,
    UsersService_SetStudentEnrollmentSelectionRequest as SetStudentEnrollmentSelectionRequest,
    UsersService_StudentSelections as StudentSelections,
    NotificationsService_SubjectEnrollmentUpdate as SubjectEnrollmentUpdate,
    NotificationsService_SubjectEnrollmentUpdateSubscription as SubjectEnrollmentUpdateSubscription,
    NotificationsService_SubjectEnrollmentUpdateSubscriptionRequest as SubjectEnrollmentUpdateSubscriptionRequest,
    SubjectTag,
    UserType,
} from '@bodin2/electives-common/proto/api'
import type { GatewayOptions } from './gateway'

export {
    AdminAddUserRequest,
    AdminBulkAddUsersRequest,
    AdminBulkDeleteUsersRequest,
    AdminChallengeResponse,
    AdminEnrollmentPatch,
    AdminGroupPatch,
    AdminListGroupsResponse,
    AdminListUsersResponse,
    AdminSetEnrollmentSubjectsRequest,
    AdminSetStudentSelectionsRequest,
    AdminSubjectPatch,
    AdminUserPatch,
    AuthenticateRequest,
    AuthenticateResponse,
    BulkSubjectEnrollmentUpdate,
    ListEnrollmentsResponse,
    ListSubjectMembersResponse,
    ListSubjectsResponse,
    type MessageFns,
    NotificationEnvelope,
    NotificationIdentify,
    RawEnrollment,
    RawGroup,
    RawSubject,
    RawUser,
    SetStudentEnrollmentSelectionRequest,
    StudentSelections,
    SubjectEnrollmentUpdate,
    SubjectEnrollmentUpdateSubscription,
    SubjectEnrollmentUpdateSubscriptionRequest,
    SubjectTag,
    UserType,
}

export interface ClientOptions {
    /**
     * Base URL for the API
     *
     * @example "https://api.example.com"
     */
    baseURL: string
    /**
     * WebSocket URL for notifications
     *
     * @example "wss://api.example.com/notifications"
     */
    notificationsURL?: string
    /**
     * Default timeout for requests in milliseconds
     */
    timeout?: number
    /**
     * Cache TTL in milliseconds
     *
     * @default 300000 // 5 minutes
     */
    cacheTTL?: number
    /**
     * Whether to automatically connect to WebSocket on login
     */
    autoConnect?: boolean

    /**
     * Gateway options for connecting to the gateway
     */
    gateway?: Partial<GatewayOptions>
}

export interface LoginOptions {
    id: number
    password: string
    clientName?: string
}

export class APIError extends Error {
    constructor(
        message: string,
        public readonly status: number,
        public readonly code?: string,
    ) {
        super(message)
        this.name = 'APIError'
    }
}

enum NetworkErrorType {
    Generic = 1,
    Timeout = 2,
}

export class NetworkError extends APIError {
    static Type = NetworkErrorType

    constructor(
        message: string,
        public readonly type: NetworkErrorType,
    ) {
        super(message, 0, 'NETWORK_ERROR')
        this.name = 'NetworkError'
    }
}

export class NotFoundError extends APIError {
    constructor(message: string) {
        super(message, 404, 'NOT_FOUND')
        this.name = 'NotFoundError'
    }
}

export class UnauthorizedError extends APIError {
    constructor(message = 'Unauthorized') {
        super(message, 401, 'UNAUTHORIZED')
        this.name = 'UnauthorizedError'
    }
}

export class ForbiddenError extends APIError {
    constructor(message = 'Forbidden') {
        super(message, 403, 'FORBIDDEN')
        this.name = 'ForbiddenError'
    }
}

export class BadRequestError extends APIError {
    constructor(message = 'Bad Request') {
        super(message, 400, 'BAD_REQUEST')
        this.name = 'BadRequestError'
    }
}

export class ConflictError extends APIError {
    constructor(message = 'Conflict') {
        super(message, 409, 'CONFLICT')
        this.name = 'ConflictError'
    }
}

export interface RateLimitInfo {
    /** Maximum number of requests allowed in the current window */
    limit: number
    /** Number of requests remaining in the current window */
    remaining: number
    /** Unix timestamp (seconds) when the rate limit resets */
    reset: number
    /** Retry after in seconds (computed from reset - now) */
    retryAfter: number
}

export class RateLimitError extends APIError {
    constructor(
        public readonly rateLimit: RateLimitInfo,
        message = 'Rate Limited',
    ) {
        super(message, 429, 'RATE_LIMITED')
        this.name = 'RateLimitError'
    }
}
