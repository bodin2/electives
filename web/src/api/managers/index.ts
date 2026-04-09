export {
    type AdminElectiveCounts,
    type AdminElectiveListEntry,
    ElectiveAdminActions,
    ElectiveManager,
} from './ElectiveManager'
export { SelectionAdminActions, SelectionManager } from './SelectionManager'
export {
    SubjectAdminActions,
    SubjectManager,
    type SubjectMembersResult,
} from './SubjectManager'
export { TeamAdminActions, TeamManager } from './TeamManager'
export { type FetchOptions, UserAdminActions, UserManager } from './UserManager'

export interface CacheableManager {
    /**
     * Clear the manager's cache
     */
    clearCache(): void
}
