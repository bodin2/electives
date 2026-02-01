import Logger from '@bodin2/electives-common/Logger'
import { createContext, onCleanup, type ParentProps, useContext } from 'solid-js'
import { createStore, reconcile } from 'solid-js/store'
import type { Client } from '../api'

const log = new Logger('EnrollmentCountsProvider')

type EnrollmentStore = {
    /** Record<ElectiveId, Record<SubjectId, Count>> */
    counts: Record<number, Record<number, number>>
    /** Increments on any enrollment change per elective */
    versions: Record<number, number>
}

type EnrollmentContextValue = {
    /** Get the enrolled count for a specific subject */
    getCount: (electiveId: number, subjectId: number) => number | undefined
    /** Get all counts for an elective */
    getElectiveCounts: (electiveId: number) => Record<number, number>
    /** Initialize counts for an elective (e.g., from loader data) */
    initializeCounts: (electiveId: number, counts: Record<number, number>) => void
    /** Get the current version for an elective's enrollment counts */
    getVersion: (electiveId: number) => number
}

const EnrollmentContext = createContext<EnrollmentContextValue>()

export function EnrollmentCountsProvider(props: ParentProps<{ client: Client }>) {
    const [store, setStore] = createStore<EnrollmentStore>({ counts: {}, versions: {} })

    const handleUpdate = (event: { electiveId: number; subjectId: number; enrolledCount: number }) => {
        log.info('Received subject enrollment update:', event)
        setStore('counts', event.electiveId, event.subjectId, event.enrolledCount)
        setStore('versions', event.electiveId, v => (v ?? 0) + 1)
    }

    const handleBulkUpdate = (event: { electiveId: number; subjectEnrolledCounts: Record<string, number> }) => {
        log.info('Received bulk subject enrollment update:', event)

        const orig = store.counts[event.electiveId]
        const deepEqual = Object.entries(event.subjectEnrolledCounts).every(
            ([subjectId, count]) => orig?.[Number(subjectId)] === count,
        )

        if (deepEqual) {
            log.info('Bulk update is identical to existing counts; skipping')
            return
        }

        setStore('counts', event.electiveId, reconcile(event.subjectEnrolledCounts))
        setStore('versions', event.electiveId, v => (v ?? 0) + 1)
    }

    props.client.on('subjectEnrollmentUpdate', handleUpdate)
    props.client.gateway.on('bulkSubjectEnrollmentUpdate', handleBulkUpdate)

    const value: EnrollmentContextValue = {
        getCount: (electiveId, subjectId) => store.counts[electiveId]?.[subjectId],
        getElectiveCounts: electiveId => store.counts[electiveId] ?? {},
        getVersion: electiveId => store.versions[electiveId] ?? 0,
        initializeCounts: (electiveId, counts) => {
            const existing = store.counts[electiveId]
            if (existing) {
                setStore('counts', electiveId, { ...counts, ...existing })
            } else {
                setStore('counts', electiveId, counts)
            }
        },
    }

    onCleanup(() => {
        props.client.off('subjectEnrollmentUpdate', handleUpdate)
        props.client.gateway.off('bulkSubjectEnrollmentUpdate', handleBulkUpdate)
    })

    return <EnrollmentContext.Provider value={value}>{props.children}</EnrollmentContext.Provider>
}

export function useEnrollmentCounts() {
    const context = useContext(EnrollmentContext)
    if (!context) {
        throw new Error('useEnrollmentCounts must be used within an EnrollmentCountsProvider')
    }
    return context
}
