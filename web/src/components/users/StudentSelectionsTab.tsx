import { createQuery } from '@tanstack/solid-query'
import { createMemo, createRenderEffect, For, type JSXElement, Show } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useEnrollmentCounts } from '~/providers/EnrollmentCountsProvider'
import { enrollmentsQueryOptions } from '~/queries/enrollments'
import { selectionsQueryOptions } from '~/queries/selections'
import { enrollmentSorter } from '~/utils'
import SectionedList from '../SectionedList'
import { useSubjectDisplayContext } from '../subjects/SubjectDisplayContext'
import SubjectListItem from '../subjects/SubjectListItem'
import type { Enrollment, Subject } from '~/api'

export interface StudentSelectionsTabProps {
    userId: number
    fallback?: JSXElement
}

export default function StudentSelectionsTab(props: StudentSelectionsTabProps) {
    const api = useAPI()
    const enrollment = useEnrollmentCounts()
    const subjectDisplayContext = useSubjectDisplayContext()

    const selectionsQuery = createQuery(() => selectionsQueryOptions(api.client, props.userId))
    const enrollmentsQuery = createQuery(() => enrollmentsQueryOptions(api.client))

    const data = () => {
        if (!selectionsQuery.data || !enrollmentsQuery.data) return undefined
        return { selections: selectionsQuery.data, enrollments: enrollmentsQuery.data }
    }

    createRenderEffect(() => {
        const d = data()
        if (!d) return

        for (const [enrollmentId] of d.selections) {
            enrollment.initializeCounts(enrollmentId, api.client.enrollments.resolveAllEnrolledCounts(enrollmentId))
        }
    })

    const groupedSelections = createMemo(() => {
        const d = data()
        if (!d) return []

        const result: Record<string, { enrollment: Enrollment; subject: Subject }[]> = {}

        for (const [enrollmentId, subject] of d.selections) {
            const en = d.enrollments.find(e => e.id === enrollmentId)
            if (!en) continue

            if (!result[en.name]) {
                result[en.name] = []
            }

            result[en.name].push({ enrollment: en, subject })
        }

        return Object.entries(result).sort(([_, [{ enrollment: enA }]], [__, [{ enrollment: enB }]]) =>
            enrollmentSorter(enA, enB),
        )
    })

    return (
        <Show when={data()}>
            <SectionedList
                items={groupedSelections()}
                fallback={props.fallback}
                renderSection={(enrollmentName, items) => (
                    <section>
                        <h1
                            class="m3-title-large padded"
                            style={{
                                position: 'sticky',
                                top: 'var(--sticky-offset)',
                                'z-index': 'var(--layer-overlay)',
                                background: 'var(--m3c-surface)',
                            }}
                        >
                            {enrollmentName}
                        </h1>
                        <ul>
                            <For each={items}>
                                {({ enrollment: en, subject }) => (
                                    <SubjectListItem
                                        subject={subject}
                                        enrollmentId={en.id}
                                        linkProps={subjectDisplayContext.viewLinkProps(en.id, subject.id)}
                                    />
                                )}
                            </For>
                        </ul>
                    </section>
                )}
            />
        </Show>
    )
}
