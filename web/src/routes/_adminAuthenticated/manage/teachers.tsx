import { createFileRoute } from '@tanstack/solid-router'
import Page from '../../../components/Page'
import { useI18n } from '../../../providers/I18nProvider'

export const Route = createFileRoute('/_adminAuthenticated/manage/teachers')({
    component: RouteComponent,
})

function RouteComponent() {
    const { string } = useI18n()

    return (
        <Page name={string.TEACHERS()} trailing={null}>
            Teachers
        </Page>
    )
}
