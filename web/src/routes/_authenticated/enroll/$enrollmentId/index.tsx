import { createQuery } from '@tanstack/solid-query'
import { createFileRoute, notFound } from '@tanstack/solid-router'
import { Show } from 'solid-js'
import Page from '~/components/Page'
import { NotFoundPageContent } from '~/components/pages/NotFoundPage'
import { BaseSubjectDisplayContext } from '~/components/subjects/SubjectDisplayContext'
import SubjectList from '~/components/subjects/SubjectList'
import { useAPI } from '~/providers/APIProvider'
import { enrollmentQueryOptions, enrollmentSubjectsQueryOptions } from '~/queries/enrollments'
import { nonNull } from '~/utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'

export const Route = createFileRoute('/_authenticated/enroll/$enrollmentId/')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    params: {
        parse: raw => ({
            enrollmentId: Number(raw.enrollmentId),
        }),
    },
    loader: async ({ context, params }) => {
        const enrollmentId = params.enrollmentId
        if (!enrollmentId) throw notFound()

        const { client, queryClient } = context
        await Promise.all([
            queryClient.ensureQueryData(enrollmentQueryOptions(client, enrollmentId)),
            queryClient.ensureQueryData(enrollmentSubjectsQueryOptions(client, enrollmentId)),
        ])
    },
    component: RouteComponent,
})

function RouteComponent() {
    const params = Route.useParams()
    const { client } = useAPI()
    const user = nonNull(client.user)

    const enrollmentQuery = createQuery(() => enrollmentQueryOptions(client, params().enrollmentId))
    const subjectsQuery = createQuery(() => ({
        ...enrollmentSubjectsQueryOptions(client, params().enrollmentId),
        notifyOnChangeProps: ['data'],
    }))

    return (
        <Show when={enrollmentQuery.data}>
            {enrollment => (
                <Page name={enrollment().name}>
                    <Show when={(subjectsQuery.data?.length ?? 0) > 0} fallback={<NotFoundPageContent />}>
                        <SubjectList
                            subjects={nonNull(subjectsQuery.data, 'Subjects not fetched')}
                            user={user}
                            enrollment={enrollment()}
                            viewLinkProps={subjectId =>
                                BaseSubjectDisplayContext.viewLinkProps(enrollment().id, subjectId)
                            }
                        />
                    </Show>
                </Page>
            )}
        </Show>
    )
}
