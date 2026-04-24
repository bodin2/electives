import { createFileRoute, Outlet } from '@tanstack/solid-router'
import { createEffect, onCleanup } from 'solid-js'
import { useSubjectDisplayContext } from '../../../components/subjects/SubjectDisplayContext'
import { useAPI } from '../../../providers/APIProvider'
import { nonNull } from '../../../utils'

export const Route = createFileRoute('/_authenticated/enroll/$electiveId')({
    component: RouteComponent,
    params: {
        parse: raw => ({
            electiveId: Number(raw.electiveId),
        }),
    },
    loader: async ({ params, context }) => {
        const elective = await context.client.electives.fetch(params.electiveId)
        return elective
    },
})

function RouteComponent() {
    const elective = Route.useLoaderData()
    const subjectDisplayContext = useSubjectDisplayContext()
    const api = useAPI()

    createEffect(() => {
        const e = elective()
        if (!e) return

        subjectDisplayContext.setUser(nonNull(api.client.user))
        subjectDisplayContext.setElective(e)
    })

    onCleanup(() => {
        subjectDisplayContext.setElective(undefined)
    })

    return <Outlet />
}
