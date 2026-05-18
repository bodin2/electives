import { createQuery } from '@tanstack/solid-query'
import { createEffect, createMemo, For, Show } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useEnrollmentCounts } from '~/providers/EnrollmentCountsProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentsQueryOptions } from '~/queries/enrollments'
import { selectionsQueryOptions } from '~/queries/selections'
import { enrollmentSorter } from '~/utils'
import SectionedList from '../SectionedList'
import { useSubjectDisplayContext } from '../subjects/SubjectDisplayContext'
import SubjectListItem from '../subjects/SubjectListItem'
import type { Enrollment, Subject } from '~/api'
export interface StudentSelectionsTabProps {
    userId: number
}

export default function StudentSelectionsTab(props: StudentSelectionsTabProps) {
    const api = useAPI()
    const enrollment = useEnrollmentCounts()
    const { string } = useI18n()
    const subjectDisplayContext = useSubjectDisplayContext()

    const selectionsQuery = createQuery(() => selectionsQueryOptions(api.client, props.userId))
    const enrollmentsQuery = createQuery(() => enrollmentsQueryOptions(api.client))

    const data = () => {
        if (!selectionsQuery.data || !enrollmentsQuery.data) return undefined
        return { selections: selectionsQuery.data, enrollments: enrollmentsQuery.data }
    }

    createEffect(() => {
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
                style={{ top: '48px', position: 'sticky' }}
                items={groupedSelections()}
                fallback={
                    <p class="text-surface-variant text-center">
                        {string.NO_X_YET({ object: string.SELECTIONS().toLowerCase() })}
                    </p>
                }
                renderSection={(enrollmentName, items) => (
                    <section>
                        <h1 class="m3-title-large padded">{enrollmentName}</h1>
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
