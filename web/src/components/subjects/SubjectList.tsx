import { SubjectTag } from '@bodin2/electives-common/proto/api'
import type { LinkProps } from '@tanstack/solid-router'
import { createEffect, createMemo, createSignal, type JSX, Show } from 'solid-js'
import { type Elective, type Subject, User } from '../../api'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { groupItems, nonNull } from '../../utils'
import { NotFoundPageContent } from '../pages/NotFoundPage'
import SectionedList from '../SectionedList'
import SubjectCategorySection from './SubjectCategorySection'
import styles from './SubjectList.module.css'

export interface SubjectListProps {
    subjects: Subject[]
    user?: User
    elective?: Elective
    editable?: boolean
    noRandom?: boolean
    headerActions?: JSX.Element
    itemActions?: (subject: Subject) => JSX.Element
    viewLinkProps?: (subjectId: number) => LinkProps
}

export default function SubjectList(props: SubjectListProps) {
    const enrollment = useEnrollmentCounts()
    const api = useAPI()
    const { string } = useI18n()
    const [query, setQuery] = createSignal('')

    createEffect(() => {
        if (props.elective) {
            enrollment.initializeCounts(props.elective.id, api.client.electives.resolveAllEnrolledCounts(props.elective.id))
        }
    })

    const subjects = () =>
        groupItems(
            props.subjects.filter(subject => {
                if (props.editable || !props.user) return true
                if (props.user.isTeacher()) return true
                if (subject.teamId !== undefined) return subject.canUserEnroll(props.user)
                return true
            }),
            s => SubjectTag[s.tag],
        )

    const filteredSubjects = createMemo(() => {
        const q = query()
        if (!q) return subjects()

        const filtered: Record<string, Subject[]> = {}
        for (const [category, subjectList] of Object.entries(subjects())) {
            const matchedSubjects = nonNull(subjectList).filter(
                subject =>
                    subject.name.toLowerCase().includes(q) ||
                    subject.teachers.some(teacher => new User(teacher).fullName.toLowerCase().includes(q)) ||
                    subject.location.toLowerCase().includes(q),
            )

            if (matchedSubjects.length > 0) {
                filtered[category] = matchedSubjects
            }
        }

        return filtered
    })

    return (
        <Show when={Object.keys(subjects()).length > 0 || props.editable} fallback={<NotFoundPageContent />}>
            <SectionedList
                items={filteredSubjects() as Record<string, Subject[]>}
                onSearch={setQuery}
                searchLabel={string.SEARCH_SUBJECTS()}
                headerActions={props.headerActions}
                noResultsFallback={<p class="padded text-surface-variant">{string.NO_RESULTS_FOUND()}</p>}
                renderSection={(category, categorySubjects, q) => (
                    <SubjectCategorySection
                        noRandom={props.noRandom}
                        defaultExpanded={q.length > 0 || props.editable}
                        maxUnexpandedShown={props.editable ? 9999 : 3}
                        category={category as keyof typeof SubjectTag}
                        subjects={nonNull(categorySubjects)}
                        headerClass={styles.header}
                        listClass={styles.list}
                        thumbnailClass={styles.subjectThumbnail}
                        editable={props.editable}
                        elective={props.elective}
                        itemActions={props.itemActions}
                        viewLinkProps={props.viewLinkProps}
                    />
                )}
            />
        </Show>
    )
}
