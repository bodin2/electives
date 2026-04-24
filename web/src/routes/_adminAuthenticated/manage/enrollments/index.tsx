import { createFileRoute } from '@tanstack/solid-router'
import ElectiveList from '../../../../components/electives/ElectiveList'
import Page from '../../../../components/Page'
import { useI18n } from '../../../../providers/I18nProvider'
import { electiveSorter } from '../../../../utils'

export const Route = createFileRoute('/_adminAuthenticated/manage/enrollments/')({
    component: RouteComponent,
    loader: async ({ context }) => {
        const electives = await context.client.electives.fetchAll()
        return electives.sort(electiveSorter)
    },
})

function RouteComponent() {
    const { string } = useI18n()
    const data = Route.useLoaderData()

    return (
        <Page name={string.ENROLLMENTS()} leading={null} trailing={null}>
            <ElectiveList electives={data()} onCardClick={() => alert('TODO?')} />
        </Page>
    )
}
