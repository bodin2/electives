export {
    type AdminEnrollmentCounts,
    type AdminEnrollmentListEntry,
    EnrollmentAdminActions,
    EnrollmentManager,
} from './EnrollmentManager'
export { GroupAdminActions, GroupManager } from './GroupManager'
export { SelectionAdminActions, SelectionManager } from './SelectionManager'
export {
    SubjectAdminActions,
    SubjectManager,
    type SubjectMembersResult,
} from './SubjectManager'
export { type FetchOptions, UserAdminActions, UserManager } from './UserManager'

export interface CacheableManager {
    /**
     * Clear the manager's cache
     */
    clearCache(): void
}
