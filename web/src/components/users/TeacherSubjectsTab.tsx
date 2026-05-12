import { createQuery } from '@tanstack/solid-query'
import { createEffect, createMemo, For, Show } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useEnrollmentCounts } from '~/providers/EnrollmentCountsProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentsQueryOptions } from '~/queries/enrollments'
import { teacherSubjectsQueryOptions } from '~/queries/users'
import { enrollmentSorter } from '~/utils'
import SectionedList from '../SectionedList'
import { useSubjectDisplayContext } from '../subjects/SubjectDisplayContext'
import SubjectListItem from '../subjects/SubjectListItem'
import type { Enrollment, Subject } from '~/api'

export interface TeacherSubjectsTabProps {
    userId: number
}

export default function TeacherSubjectsTab(props: TeacherSubjectsTabProps) {
    const { client } = useAPI()
    const enrollment = useEnrollmentCounts()
    const { string } = useI18n()
    const subjectDisplayContext = useSubjectDisplayContext()

    const subjectsQuery = createQuery(() => ({
        ...teacherSubjectsQueryOptions(client, props.userId),
        notifyOnChangeProps: ['data'],
    }))
    const enrollmentsQuery = createQuery(() => ({ ...enrollmentsQueryOptions(client), notifyOnChangeProps: ['data'] }))

    const data = () => {
        if (!subjectsQuery.data || !enrollmentsQuery.data) return undefined
        return { subjects: subjectsQuery.data, enrollments: enrollmentsQuery.data }
    }

    const groupedSubjects = createMemo(() => {
        const d = data()
        if (!d) return []

        const result: Record<string, { enrollment: Enrollment; subject: Subject }[]> = {}

        for (const [enrollmentId, subject] of d.subjects) {
            const enrollment_ = d.enrollments.find(e => e.id === enrollmentId)
            if (!enrollment_) continue

            if (!result[enrollment_.name]) {
                result[enrollment_.name] = []
            }

            result[enrollment_.name].push({ enrollment: enrollment_, subject })
        }

        return Object.entries(result).sort(([_, [{ enrollment: enA }]], [__, [{ enrollment: enB }]]) =>
            enrollmentSorter(enA, enB),
        )
    })

    createEffect(() => {
        const d = data()
        if (!d) return

        for (const [enrollmentId] of d.subjects) {
            enrollment.initializeCounts(enrollmentId, client.enrollments.resolveAllEnrolledCounts(enrollmentId))
        }
    })

    return (
        <Show when={data()}>
            <SectionedList
                style={{ top: '48px', position: 'sticky' }}
                items={groupedSubjects()}
                fallback={
                    <p class="text-surface-variant text-center">
                        {string.NO_X_YET({ object: string.SUBJECTS().toLowerCase() })}
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
                                        linkProps={subjectDisplayContext.viewLinkProps(en.id, subject.id, 'members')}
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
