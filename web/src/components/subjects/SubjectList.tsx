import { createEffect, createMemo, createSignal, type JSX, Show } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useEnrollmentCounts } from '~/providers/EnrollmentCountsProvider'
import { useI18n } from '~/providers/I18nProvider'
import { groupMapItems, nonNull } from '~/utils'
import SectionedList from '../SectionedList'
import SubjectCategorySection from './SubjectCategorySection'
import type { SubjectTag } from '@bodin2/electives-common/proto/api'
import type { LinkProps } from '@tanstack/solid-router'
import type { Enrollment, Subject, User } from '~/api'

export interface SubjectListProps {
    subjects: Subject[]
    user?: User
    enrollment?: Enrollment
    editable?: boolean
    noRandom?: boolean
    headerActions?: JSX.Element
    searchContainerClass?: string
    itemActions?: (subject: Subject) => JSX.Element
    viewLinkProps?: (subjectId: number) => LinkProps
    selectedIds?: number[]
    emptyElement?: JSX.Element
    onSubjectClick?: (subject: Subject) => void
}

export default function SubjectList(props: SubjectListProps) {
    const enrollment = useEnrollmentCounts()
    const { client } = useAPI()
    const { string } = useI18n()
    const [query, setQuery] = createSignal('')

    createEffect(() => {
        if (props.enrollment) {
            enrollment.initializeCounts(
                props.enrollment.id,
                client.enrollments.resolveAllEnrolledCounts(props.enrollment.id),
            )
        }
    })

    const teachersOf = (en: Enrollment | undefined, subject: Subject) => {
        if (!en) return []
        return client.subjects.resolveTeachers(en.id, subject.id)
    }

    const subjects = () => {
        const grouped = [
            ...groupMapItems<Subject, SubjectTag | string>(
                props.subjects.filter(subject => {
                    if (props.editable || !props.user) return true
                    if (props.user.isTeacher()) return true
                    if (subject.groupId !== undefined) return subject.canUserEnroll(props.user)
                    return true
                }),
                s => s.tag,
            ),
        ].sort(([a], [b]) => {
            if (typeof a === 'number' && typeof b === 'number') return a - b
            if (typeof a === 'number') return -1
            if (typeof b === 'number') return 1
            return a.localeCompare(b)
        })

        const u = props.user
        if (u?.isTeacher() && props.enrollment) {
            const teacherSubjects = props.subjects.filter(s =>
                teachersOf(props.enrollment, s)?.some(t => t.id === u.id),
            )
            grouped.unshift([string.MY_SUBJECTS(), teacherSubjects])
        }

        return grouped
    }

    const filteredSubjects = createMemo(() => {
        const subjectsList = subjects()

        const q = query()
        if (!q) return subjectsList

        const filterSubjects = (subject: Subject) =>
            subject.name.toLowerCase().includes(q) ||
            teachersOf(props.enrollment, subject)?.some(teacher => teacher.displayName.toLowerCase().includes(q)) ||
            subject.location.toLowerCase().includes(q)

        return subjectsList
            .map(
                ([category, categorySubjects]) =>
                    [category, nonNull(categorySubjects).filter(filterSubjects)] as const satisfies [
                        string | SubjectTag,
                        Subject[],
                    ],
            )
            .filter(([_, categorySubjects]) => categorySubjects.length > 0)
    })

    return (
        <Show when={subjects().length > 0 || props.editable} fallback={props.emptyElement}>
            <SectionedList
                items={filteredSubjects()}
                onSearch={setQuery}
                searchLabel={string.SEARCH_SUBJECTS()}
                searchContainerClass={props.searchContainerClass}
                headerActions={props.headerActions}
                fallback={props.emptyElement}
                noResultsFallback={<p class="padded text-surface-variant">{string.NO_RESULTS_FOUND()}</p>}
                renderSection={(category, categorySubjects, q) => (
                    <SubjectCategorySection
                        noRandom={props.noRandom}
                        defaultExpanded={q.length > 0 || props.editable}
                        maxUnexpandedShown={props.editable ? 9999 : 3}
                        category={category as keyof typeof SubjectTag}
                        subjects={nonNull(categorySubjects)}
                        editable={props.editable}
                        enrollment={props.enrollment}
                        itemActions={props.itemActions}
                        viewLinkProps={props.viewLinkProps}
                        selectedIds={props.selectedIds}
                        onSubjectClick={props.onSubjectClick}
                    />
                )}
            />
        </Show>
    )
}
