import { createQuery } from '@tanstack/solid-query'
import { createFileRoute, notFound } from '@tanstack/solid-router'
import { Show } from 'solid-js'
import Page from '../../../../components/Page'
import { NotFoundPageContent } from '../../../../components/pages/NotFoundPage'
import { BaseSubjectDisplayContext } from '../../../../components/subjects/SubjectDisplayContext'
import SubjectList from '../../../../components/subjects/SubjectList'
import { useAPI } from '../../../../providers/APIProvider'
import { electiveQueryOptions, electiveSubjectsQueryOptions } from '../../../../queries/electives'
import { nonNull } from '../../../../utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    params: {
        parse: raw => ({
            electiveId: Number(raw.electiveId),
        }),
    },
    loader: async ({ context, params }) => {
        const electiveId = params.electiveId
        if (!electiveId) throw notFound()

        const { client, queryClient } = context
        await Promise.all([
            queryClient.ensureQueryData(electiveQueryOptions(client, electiveId)),
            queryClient.ensureQueryData(electiveSubjectsQueryOptions(client, electiveId)),
        ])
    },
    component: RouteComponent,
})

function RouteComponent() {
    const params = Route.useParams()
    const { client } = useAPI()
    const user = nonNull(client.user)

    const electiveQuery = createQuery(() => electiveQueryOptions(client, params().electiveId))
    const subjectsQuery = createQuery(() => electiveSubjectsQueryOptions(client, params().electiveId))

    return (
        <Show when={electiveQuery.data}>
            {elective => (
                <Page name={elective().name}>
                    <Show when={(subjectsQuery.data?.length ?? 0) > 0} fallback={<NotFoundPageContent />}>
                        <SubjectList
                            subjects={nonNull(subjectsQuery.data, 'Subjects not fetched')}
                            user={user}
                            elective={elective()}
                            viewLinkProps={subjectId =>
                                BaseSubjectDisplayContext.viewLinkProps(elective().id, subjectId)
                            }
                        />
                    </Show>
                </Page>
            )}
        </Show>
    )
}
