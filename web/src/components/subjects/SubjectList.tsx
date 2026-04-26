import { SubjectTag } from '@bodin2/electives-common/proto/api'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createEffect, createMemo, createSignal, Show } from 'solid-js'
import { type Subject, User } from '../../api'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { groupItems, nonNull } from '../../utils'
import LinkButton from '../LinkButton'
import { NotFoundPageContent } from '../pages/NotFoundPage'
import SectionedList from '../SectionedList'
import SubjectCategorySection from './SubjectCategorySection'
import { useSubjectDisplayContext } from './SubjectDisplayContext'
import styles from './SubjectList.module.css'

export interface SubjectListProps {
    subjects: Subject[]
    noRandom?: boolean
}

export default function SubjectList(props: SubjectListProps) {
    const enrollment = useEnrollmentCounts()
    const api = useAPI()
    const { string } = useI18n()
    const ctx = useSubjectDisplayContext()
    const [query, setQuery] = createSignal('')

    createEffect(() => {
        if (ctx.elective) {
            enrollment.initializeCounts(ctx.elective.id, api.client.electives.resolveAllEnrolledCounts(ctx.elective.id))
        }
    })

    const subjects = () =>
        groupItems(
            props.subjects.filter(subject => {
                if (ctx.editable || !ctx.user) return true
                if (ctx.user.isTeacher()) return true
                if (subject.teamId !== undefined) return subject.canUserEnroll(ctx.user)
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
        <Show when={Object.keys(subjects()).length > 0 || ctx.editable} fallback={<NotFoundPageContent />}>
            <SectionedList
                items={filteredSubjects() as Record<string, Subject[]>}
                onSearch={setQuery}
                searchLabel={string.SEARCH_SUBJECTS()}
                headerActions={
                    <Show when={ctx.editable}>
                        <LinkButton {...ctx.createLinkProps()} variant="filled" icon={PlusIcon}>
                            {string.CREATE_SUBJECT()}
                        </LinkButton>
                    </Show>
                }
                noResultsFallback={<p class="padded text-surface-variant">{string.NO_RESULTS_FOUND()}</p>}
                renderSection={(category, categorySubjects, q) => (
                    <SubjectCategorySection
                        noRandom={props.noRandom}
                        defaultExpanded={q.length > 0 || ctx.editable}
                        maxUnexpandedShown={ctx.editable ? 9999 : 3}
                        category={category as keyof typeof SubjectTag}
                        subjects={nonNull(categorySubjects)}
                        headerClass={styles.header}
                        listClass={styles.list}
                        thumbnailClass={styles.subjectThumbnail}
                    />
                )}
            />
        </Show>
    )
}
