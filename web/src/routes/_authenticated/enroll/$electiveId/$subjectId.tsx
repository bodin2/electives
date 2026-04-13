import { createFileRoute } from '@tanstack/solid-router'
import { NotFoundError } from '../../../../api'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import SubjectInfo from '../../../../components/subjects/SubjectInfo'
import { nonNull } from '../../../../utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/$subjectId')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    params: {
        parse: raw => ({
            electiveId: Number(raw.electiveId),
            subjectId: Number(raw.subjectId),
        }),
    },
    loader: async ({ context, params }) => {
        const electiveId = params.electiveId
        const subjectId = params.subjectId
        const user = nonNull(context.client.user)

        const [subject, elective, selections] = await Promise.all([
            context.client.subjects.fetch({
                subjectId,
                electiveId,
                withDescription: true,
            }),
            context.client.electives.fetch(electiveId),
            user.isStudent() ? context.client.selections.fetch('@me') : null,
        ])

        const selectedSubject = selections?.get(electiveId)
        const initialEnrolledCounts = context.client.electives.resolveAllEnrolledCounts(electiveId)

        return {
            user,
            subject,
            elective,
            selectedSubject,
            initialEnrolledCounts,
        }
    },
    errorComponent: props => {
        if (props.error instanceof NotFoundError) return <NotFoundPage />
        throw props.error
    },
    component: RouteComponent,
})

function RouteComponent() {
    const data = Route.useLoaderData()

    return (
        <Page name={data().subject.name}>
            <SubjectInfo
                user={data().user}
                subject={data().subject}
                elective={data().elective}
                selectedSubject={data().selectedSubject}
                initialEnrolledCounts={data().initialEnrolledCounts}
            />
        </Page>
    )
}
