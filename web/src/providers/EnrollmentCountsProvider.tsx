import Logger from '@bodin2/electives-common/Logger'
import { createContext, onCleanup, type ParentProps, useContext } from 'solid-js'
import { createStore, reconcile } from 'solid-js/store'
import { nonNull } from '../utils'
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
    getCount: (electiveId: number, subjectId: number) => number
    /** Get all counts for an elective */
    getElectiveCounts: (electiveId: number) => Record<number, number>
    /** Initialize counts for an elective (e.g., from loader data) */
    initializeCounts: (electiveId: number, counts: Record<number, number>) => void
    /** Get the current version for an elective's enrollment counts */
    getVersion: (electiveId: number) => number
    // TODO: Find a better way to emit refresh than this
    /** Increment the version for an elective's enrollment counts */
    bumpVersion: (electiveId: number) => void
    /** Manually set count to provide local updates immediately */
    setCount: (electiveId: number, subjectId: number, setter: (current: number) => number) => void
}

const EnrollmentContext = createContext<EnrollmentContextValue>()

export function EnrollmentCountsProvider(props: ParentProps<{ client: Client<unknown> }>) {
    const [store, setStore] = createStore<EnrollmentStore>({ counts: {}, versions: {} })

    // @ts-expect-error: Exposing to DEV
    if (import.meta.env.DEV) globalThis.$ecp = store

    const handleUpdate = (event: { electiveId: number; subjectId: number; enrolledCount: number }) => {
        log.info('Received subject enrollment update:', event)
        setStore('counts', event.electiveId, event.subjectId, event.enrolledCount)
        setStore('versions', event.electiveId, v => (v ?? 0) + 1)
    }

    const handleBulkUpdate = (event: { electiveId: number; subjectEnrolledCounts: Record<string, number> }) => {
        log.info('Received bulk subject enrollment update:', event)

        let changed = false
        for (const [subjectId, count] of Object.entries(event.subjectEnrolledCounts)) {
            const sid = Number(subjectId)

            if (store.counts[event.electiveId]?.[sid] !== count) {
                setStore('counts', event.electiveId, sid, count)
                changed = true
            }
        }

        if (changed) {
            setStore('versions', event.electiveId, v => (v ?? 0) + 1)
        } else {
            log.info('Bulk update is identical to existing counts; skipping')
        }
    }

    props.client.on('subjectEnrollmentUpdate', handleUpdate)
    props.client.on('bulkSubjectEnrollmentUpdate', handleBulkUpdate)

    // Sync from cache in case we missed initial events
    for (const [key, count] of props.client.subjects.enrolledCountCache.entries()) {
        const [electiveId, subjectId] = key.split(':').map(Number)
        if (!Number.isNaN(electiveId) && !Number.isNaN(subjectId)) {
            setStore('counts', electiveId, { [subjectId]: count })
            setStore('versions', electiveId, v => (v ?? 0) + 1)
        }
    }

    const value: EnrollmentContextValue = {
        getCount: (electiveId, subjectId) => store.counts[electiveId]?.[subjectId] ?? 0,
        getElectiveCounts: electiveId => store.counts[electiveId] ?? {},
        getVersion: electiveId => store.versions[electiveId] ?? 0,
        bumpVersion: electiveId => {
            const counts = props.client.electives.resolveAllEnrolledCounts(electiveId)
            if (Object.keys(counts).length > 0) {
                setStore('counts', electiveId, reconcile(counts))
            }
            setStore('versions', electiveId, v => (v ?? 0) + 1)
        },
        initializeCounts: (electiveId, counts) => {
            const existing = store.counts[electiveId]
            if (existing) {
                setStore('counts', electiveId, reconcile({ ...existing, ...counts }))
            } else {
                setStore('counts', electiveId, reconcile(counts))
            }
        },
        setCount: (electiveId, subjectId, setter) => {
            setStore('counts', electiveId, subjectId, setter)
            setStore('versions', electiveId, it => it + 1)
        },
    }

    onCleanup(() => {
        props.client.off('subjectEnrollmentUpdate', handleUpdate)
        props.client.off('bulkSubjectEnrollmentUpdate', handleBulkUpdate)
    })

    return <EnrollmentContext.Provider value={value}>{props.children}</EnrollmentContext.Provider>
}

export const useEnrollmentCounts = () =>
    nonNull(useContext(EnrollmentContext), 'useEnrollmentCounts must be used within an EnrollmentCountsProvider')
