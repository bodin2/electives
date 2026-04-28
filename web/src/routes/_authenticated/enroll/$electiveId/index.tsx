import { createFileRoute, notFound } from '@tanstack/solid-router'
import Page from '../../../../components/Page'
import { BaseSubjectDisplayContext } from '../../../../components/subjects/SubjectDisplayContext'
import SubjectList from '../../../../components/subjects/SubjectList'
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

        const user = nonNull(context.client.user)
        const elective = await context.client.electives.fetch(electiveId)
        const subjects = await context.client.electives.fetchSubjects(electiveId)

        return { subjects, user, elective }
    },
    component: RouteComponent,
})

function RouteComponent() {
    const data = Route.useLoaderData()

    return (
        <Page name={data().elective.name}>
            <SubjectList
                subjects={data().subjects}
                user={data().user}
                elective={data().elective}
                viewLinkProps={subjectId => BaseSubjectDisplayContext.viewLinkProps(data().elective.id, subjectId)}
            />
        </Page>
    )
}
