import { createFileRoute, notFound } from '@tanstack/solid-router'
import Page from '../../../../components/Page'
import { useSubjectDisplayContext } from '../../../../components/subjects/SubjectDisplayContext'
import SubjectList from '../../../../components/subjects/SubjectList'
import { nonNull } from '../../../../utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    loader: async ({ context, params }) => {
        const electiveId = params.electiveId
        if (!electiveId) throw notFound()

        const user = nonNull(context.client.user)
        const subjects = await context.client.electives.fetchSubjects(electiveId)

        return { subjects, user }
    },
    component: RouteComponent,
})

function RouteComponent() {
    const data = Route.useLoaderData()
    const subjectDisplayContext = useSubjectDisplayContext()

    return (
        <Page name={nonNull(subjectDisplayContext.elective).name}>
            <SubjectList subjects={data().subjects} />
        </Page>
    )
}
