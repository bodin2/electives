import { SubjectTag } from '@bodin2/electives-common/proto/api'
import MagnifyIcon from '@iconify-icons/mdi/magnify'
import { TextField } from 'm3-solid'
import { createEffect, createMemo, createSignal, For, Show } from 'solid-js'
import { type Elective, type Subject, User } from '../../api'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { debounce, groupItems } from '../../utils'
import { NotFoundPageContent } from '../pages/NotFoundPage'
import { VStack } from '../Stack'
import SubjectCategorySection from './SubjectCategorySection'
import styles from './SubjectList.module.css'

export interface SubjectListProps {
    elective: Elective
    subjects: Subject[]
    user: User
    initialEnrolledCounts: Record<number, number>
}

export default function SubjectList(props: SubjectListProps) {
    const enrollment = useEnrollmentCounts()
    const { string } = useI18n()
    const [query, setQuery] = createSignal('')

    const updateQuery = debounce((value: string) => {
        setQuery(value.toLowerCase())
    }, 250)

    createEffect(() => {
        enrollment.initializeCounts(props.elective.id, props.initialEnrolledCounts)
    })

    const subjects = () =>
        groupItems(
            props.subjects.filter(subject => {
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
            const matchedSubjects = subjectList!.filter(
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

    // Try to show at max 2 - 3 items per category
    // But if one category has less than 3, show that amount instead if more than 2 items
    // const maxItemsShownUnexpanded = createMemo(() => {
    //     let least = 3
    //     const subjectsData = filteredSubjects()
    //     if (!subjectsData) return least

    //     for (const subjectList of Object.values(subjectsData)) {
    //         if (subjectList.length < least) {
    //             least = subjectList.length
    //         }
    //     }

    //     return Math.min(Math.max(least, 2), 3)
    // })

    return (
        <Show when={Object.keys(subjects()).length > 0} fallback={<NotFoundPageContent />}>
            <VStack gap={0}>
                <div class={styles.searchContainer}>
                    <TextField
                        variant="filled"
                        leadingIcon={MagnifyIcon}
                        class={styles.search}
                        label={string.SEARCH_SUBJECTS()}
                        onInput={e => updateQuery(e.currentTarget.value)}
                    />
                </div>
                <div class={styles.grid}>
                    <For
                        each={Object.entries(filteredSubjects())}
                        fallback={<p class="padded text-surface-variant">{string.NO_RESULTS_FOUND()}</p>}
                    >
                        {([category, categorySubjects]) => (
                            <SubjectCategorySection
                                defaultExpanded={query().length > 0}
                                maxUnexpandedShown={3}
                                electiveId={props.elective.id}
                                category={category as keyof typeof SubjectTag}
                                subjects={categorySubjects!}
                                headerClass={styles.header}
                                listClass={styles.list}
                                thumbnailClass={styles.subjectThumbnail}
                            />
                        )}
                    </For>
                </div>
            </VStack>
        </Show>
    )
}
