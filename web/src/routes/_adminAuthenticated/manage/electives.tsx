import { createFileRoute } from '@tanstack/solid-router'
import Page from '../../../components/Page'
import { useI18n } from '../../../providers/I18nProvider'

export const Route = createFileRoute('/_adminAuthenticated/manage/electives')({
    component: RouteComponent,
})

function RouteComponent() {
    const { string } = useI18n()

    return <Page name={string.ELECTIVES()}>Electives</Page>
}
