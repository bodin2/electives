import { createQuery } from '@tanstack/solid-query'
import { Button } from 'm3-solid'
import { Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { electiveSubjectsQueryOptions } from '../../queries/electives'
import SubjectList from '../subjects/SubjectList'
import { useElectiveInfoContext } from './ElectiveInfo'

export default function ElectiveSubjectsTab(props: { stickyOffset?: number }) {
    const ctx = useElectiveInfoContext()
    const { client } = useAPI()

    const subjectsQuery = createQuery(() => electiveSubjectsQueryOptions(client, ctx.elective.id))

    return (
        <Show when={subjectsQuery.data}>
            {data => (
                <Show when={data().length > 0} fallback={'IMPORT SUBJECTS TODO'}>
                    <div style={{ '--sticky-offset': `${props.stickyOffset ?? 48}px` }}>
                        <SubjectList
                            headerActions={<Button>TODO ADD SUBJECTS</Button>}
                            subjects={data()}
                            elective={ctx.elective}
                            editable={false}
                            viewLinkProps={subjectId => ({
                                to: '/manage/subjects/$subjectId',
                                params: { subjectId: String(subjectId) },
                                search: { enrollment_id: ctx.elective.id },
                            })}
                        />
                    </div>
                </Show>
            )}
        </Show>
    )
}
