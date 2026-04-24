import { createFileRoute } from '@tanstack/solid-router'
import { createEffect, onCleanup } from 'solid-js'
import { NotFoundError } from '../../../../api'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { useSubjectDisplayContext } from '../../../../components/subjects/SubjectDisplayContext'
import SubjectInfo from '../../../../components/subjects/SubjectInfo'
import { nonNull } from '../../../../utils'
import { AUTHENTICATED_ROUTE_DEFAULTS } from '../../../_authenticated'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId/$subjectId')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    params: {
        parse: raw => ({
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

        return {
            user,
            subject,
            elective,
            selectedSubject,
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
    const ctx = useSubjectDisplayContext()

    createEffect(() => {
        ctx.setSubject(data().subject)
        ctx.setElective(data().elective)
        ctx.setUser(data().user)
    })

    onCleanup(() => {
        ctx.setSubject(undefined)
        ctx.setUser(undefined)
    })

    return (
        <Page name={data().subject.name}>
            <SubjectInfo selectedSubject={data().selectedSubject} />
        </Page>
    )
}
