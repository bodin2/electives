import { createEffect, createMemo, createResource, For, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import SectionedList from '../SectionedList'
import { useSubjectDisplayContext } from '../subjects/SubjectDisplayContext'
import SubjectListItem from '../subjects/SubjectListItem'
import type { Elective, Subject } from '../../api'

export interface StudentSelectionsTabProps {
    userId: number
}

export default function StudentSelectionsTab(props: StudentSelectionsTabProps) {
    const api = useAPI()
    const enrollment = useEnrollmentCounts()
    const { string } = useI18n()
    const subjectDisplayContext = useSubjectDisplayContext()

    const [data] = createResource(async () => {
        const [selections, electives] = await Promise.all([
            api.client.selections.fetch(props.userId),
            api.client.electives.fetchAll(),
        ])

        return { selections, electives }
    })

    createEffect(() => {
        const d = data()
        if (!d) return

        for (const [electiveId] of d.selections) {
            enrollment.initializeCounts(electiveId, api.client.electives.resolveAllEnrolledCounts(electiveId))
        }
    })

    const groupedSelections = createMemo(() => {
        const d = data()
        if (!d) return {}

        const result: Record<string, { elective: Elective; subject: Subject }[]> = {}

        for (const [electiveId, subject] of d.selections) {
            const elective = d.electives.find(e => e.id === electiveId)
            if (!elective) continue

            if (!result[elective.name]) {
                result[elective.name] = []
            }
            result[elective.name].push({ elective, subject })
        }

        return result
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
                renderSection={(electiveName, items) => (
                    <section>
                        <h1 class="m3-title-large padded">{electiveName}</h1>
                        <ul>
                            <For each={items}>
                                {({ elective, subject }) => (
                                    <SubjectListItem
                                        subject={subject}
                                        electiveId={elective.id}
                                        linkProps={subjectDisplayContext.viewLinkProps(elective.id, subject.id)}
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
