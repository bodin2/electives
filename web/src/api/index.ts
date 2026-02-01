export { Cache, type CacheEntry, type CacheOptions } from './cache'
export {
    Client,
    type ClientEventHandler,
    type ClientEventMap,
    type ClientEventNames,
    type SubjectEnrollmentUpdateEvent,
} from './client'
export {
    Gateway,
    type GatewayEventHandler,
    type GatewayEventMap,
    type GatewayEventNames,
    type GatewayOptions,
    GatewayStatus,
} from './gateway'
export {
    ElectiveManager,
    type FetchOptions,
    SelectionManager,
    SubjectManager,
    type SubjectMembersResult,
    UserManager,
} from './managers'
export { RESTClient, type RESTOptions, type RequestOptions } from './rest'
export { Elective, Subject, Team, User } from './structures'
export {
    APIError,
    type AuthenticateRequest,
    type AuthenticateResponse,
    BadRequestError,
    type BulkSubjectEnrollmentUpdate,
    type ClientOptions,
    ConflictError,
    ForbiddenError,
    type GetStudentSelectionsResponse,
    type ListElectivesResponse,
    type ListSubjectMembersResponse,
    type ListSubjectsResponse,
    type LoginOptions,
    NotFoundError,
    type NotificationEnvelope,
    type NotificationIdentify,
    RateLimitError,
    type RawElective,
    type RawSubject,
    type RawTeam,
    type RawUser,
    type SetStudentElectiveSelectionRequest,
    type SubjectEnrollmentUpdate,
    type SubjectEnrollmentUpdateSubscription,
    type SubjectEnrollmentUpdateSubscriptionRequest,
    SubjectTag,
    UnauthorizedError,
    UserType,
} from './types'
