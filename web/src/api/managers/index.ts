export { ElectiveManager, SubjectManager, type SubjectMembersResult } from './ElectiveManager'
export { type SelectionFetchOptions, SelectionManager } from './SelectionManager'
export { type FetchOptions, UserManager } from './UserManager'

export interface CacheableManager {
    /**
     * Clear the manager's cache
     */
    clearCache(): void
}
