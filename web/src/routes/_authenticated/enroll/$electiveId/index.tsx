import { type Subject, SubjectTag } from '@bodin2/electives-common/proto/api'
import MagnifyIcon from '@iconify-icons/mdi/magnify'
import { createFileRoute, notFound } from '@tanstack/solid-router'
import { TextField } from 'm3-solid'
import { createEffect, createMemo, createSignal, For, Show } from 'solid-js'
import { User } from '../../../../api'
import Page from '../../../../components/Page'
import { NotFoundPageContent } from '../../../../components/pages/NotFoundPage'
import { VStack } from '../../../../components/Stack'
import SubjectCategorySection from '../../../../components/subjects/SubjectCategorySection'
import { useEnrollmentCounts } from '../../../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { debounce, groupItems, nonNull } from '../../../../utils'
import styles from './index.module.css'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/')({
    params: {
        parse: raw => ({
            electiveId: Number(raw.electiveId),
        }),
    },
    loader: async ({ context, params }) => {
        const electiveId = params.electiveId
        if (!electiveId) throw notFound()

        const user = nonNull(context.client.user)

        const [elective, subjects] = await Promise.all([
            context.client.electives.fetch(electiveId),
            context.client.electives.fetchSubjects(electiveId),
        ])

        const initialEnrolledCounts = context.client.electives.resolveAllEnrolledCounts(electiveId)

        return { elective, subjects, initialEnrolledCounts, user }
    },
    component: RouteComponent,
})

function RouteComponent() {
    const enrollment = useEnrollmentCounts()
    const data = Route.useLoaderData()
    const params = Route.useParams()
    const { string } = useI18n()
    const electiveId = () => params().electiveId
    const [query, setQuery] = createSignal('')

    const updateQuery = debounce((value: string) => {
        setQuery(value.toLowerCase())
    }, 250)

    // Initialize enrollment counts from loader data into the global store
    // The provider will merge this with any live updates it already received
    createEffect(() => {
        enrollment.initializeCounts(electiveId(), data().initialEnrolledCounts)
    })

    const elective = () => data().elective
    const subjects = () =>
        groupItems(
            data().subjects.filter(subject => {
                // Teachers can see all subjects
                if (data().user.isTeacher()) return true

                if (subject.teamId !== undefined) return subject.canUserEnroll(data().user)
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

    // Try to show at max 3 items per category
    // But if one category has less than 3, show that amount instead
    const maxItemsShownUnexpanded = createMemo(() => {
        let least = 3
        const subjectsData = filteredSubjects()
        if (!subjectsData) return least

        for (const subjectList of Object.values(subjectsData)) {
            if (subjectList.length < least) {
                least = subjectList.length
            }
        }

        return Math.min(least, 3)
    })

    return (
        <Page name={elective()?.name}>
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
                                    maxUnexpandedShown={maxItemsShownUnexpanded()}
                                    electiveId={electiveId()}
                                    category={category as keyof typeof SubjectTag}
                                    subjects={categorySubjects}
                                    headerClass={styles.header}
                                    listClass={styles.list}
                                    thumbnailClass={styles.subjectThumbnail}
                                />
                            )}
                        </For>
                    </div>
                </VStack>
            </Show>
        </Page>
    )
}
