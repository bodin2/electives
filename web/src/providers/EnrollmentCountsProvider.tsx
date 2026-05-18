import Logger from '@bodin2/electives-common/Logger'
import { createContext, onCleanup, type ParentProps, useContext } from 'solid-js'
import { createStore, produce, reconcile } from 'solid-js/store'
import { nonNull } from '~/utils'
import type { Client } from '~/api'

const log = new Logger('EnrollmentCountsProvider')

type EnrollmentStore = {
    /** Record<EnrollmentId, Record<SubjectId, Count>> */
    counts: Record<number, Record<number, number>>
    /** Increments on any enrollment change per enrollment */
    versions: Record<number, number>
}

type EnrollmentContextValue = {
    /** Get the enrolled count for a specific subject */
    getCount: (enrollmentId: number, subjectId: number) => number | undefined
    /** Get all counts for an enrollment */
    getEnrollmentCounts: (enrollmentId: number) => Record<number, number>
    /** Initialize counts for an enrollment (e.g., from loader data) */
    initializeCounts: (enrollmentId: number, counts: Record<number, number>) => void
    /** Get the current version for an enrollment's enrollment counts */
    getVersion: (enrollmentId: number) => number
    // TODO: Find a better way to emit refresh than this
    /** Increment the version for an enrollment's enrollment counts */
    bumpVersion: (enrollmentId: number) => void
    /** Manually set count to provide local updates immediately */
    setCount: (enrollmentId: number, subjectId: number, setter: (current: number) => number) => void
}

const EnrollmentContext = createContext<EnrollmentContextValue>()

export function EnrollmentCountsProvider(props: ParentProps<{ client: Client<unknown> }>) {
    const [store, setStore] = createStore<EnrollmentStore>({ counts: {}, versions: {} })

    // @ts-expect-error: Exposing to DEV
    if (import.meta.env.DEV) globalThis.$ecp = store

    const handleUpdate = (event: { enrollmentId: number; subjectId: number; enrolledCount: number }) => {
        log.info('Received subject enrollment update:', event)
        setStore('counts', event.enrollmentId, event.subjectId, event.enrolledCount)
        setStore('versions', event.enrollmentId, v => (v ?? 0) + 1)
    }

    const handleBulkUpdate = (event: { enrollmentId: number; subjectEnrolledCounts: Record<string, number> }) => {
        log.info('Received bulk subject enrollment update:', event)

        let changed = false
        const updatedSubjectIds = new Set<number>()
        for (const [subjectId, count] of Object.entries(event.subjectEnrolledCounts)) {
            const sid = Number(subjectId)
            updatedSubjectIds.add(sid)

            if (!store.counts[event.enrollmentId]) {
                setStore('counts', event.enrollmentId, { [sid]: count })
                changed = true
                continue
            }

            if (store.counts[event.enrollmentId]?.[sid] !== count) {
                setStore('counts', event.enrollmentId, sid, count)
                changed = true
            }
        }

        // The bulk update is authoritative for this enrollment — drop any subject the server
        // didn't include this time so stale counts don't linger.
        const existing = store.counts[event.enrollmentId]
        if (existing) {
            const stale = Object.keys(existing)
                .map(Number)
                .filter(sid => !updatedSubjectIds.has(sid))
            if (stale.length > 0) {
                log.info('Dropping subjects missing from bulk update:', stale)
                setStore(
                    'counts',
                    event.enrollmentId,
                    produce(counts => {
                        for (const sid of stale) delete counts[sid]
                    }),
                )
                changed = true
            }
        }

        if (changed) {
            setStore('versions', event.enrollmentId, v => (v ?? 0) + 1)
        } else {
            log.info('Bulk update is identical to existing counts; skipping')
        }
    }

    props.client.on('subjectEnrollmentUpdate', handleUpdate)
    props.client.on('bulkSubjectEnrollmentUpdate', handleBulkUpdate)

    // Sync from cache in case we missed initial events
    for (const [key, count] of props.client.subjects.enrolledCountCache.entries()) {
        const [enrollmentId, subjectId] = key.split(':').map(Number)
        if (!Number.isNaN(enrollmentId) && !Number.isNaN(subjectId)) {
            setStore('counts', enrollmentId, { [subjectId]: count })
            setStore('versions', enrollmentId, v => (v ?? 0) + 1)
        }
    }

    const value: EnrollmentContextValue = {
        getCount: (enrollmentId, subjectId) => store.counts[enrollmentId]?.[subjectId],
        getEnrollmentCounts: enrollmentId => store.counts[enrollmentId] ?? {},
        getVersion: enrollmentId => store.versions[enrollmentId] ?? 0,
        bumpVersion: enrollmentId => {
            const counts = props.client.enrollments.resolveAllEnrolledCounts(enrollmentId)
            if (Object.keys(counts).length > 0) {
                setStore('counts', enrollmentId, reconcile(counts))
            }
            setStore('versions', enrollmentId, v => (v ?? 0) + 1)
        },
        initializeCounts: (enrollmentId, counts) => {
            const existing = store.counts[enrollmentId]
            if (existing) {
                setStore('counts', enrollmentId, reconcile({ ...existing, ...counts }))
            } else {
                setStore('counts', enrollmentId, reconcile(counts))
            }
        },
        setCount: (enrollmentId, subjectId, setter) => {
            setStore('counts', enrollmentId, subjectId, setter)
            setStore('versions', enrollmentId, it => it + 1)
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
