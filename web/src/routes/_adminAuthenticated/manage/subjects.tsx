import { createFileRoute } from '@tanstack/solid-router'
import Page from '../../../components/Page'
import { useI18n } from '../../../providers/I18nProvider'

export const Route = createFileRoute('/_adminAuthenticated/manage/subjects')({
    component: RouteComponent,
})

function RouteComponent() {
    const { string } = useI18n()

    return (
        <Page name={string.SUBJECTS()} trailing={null}>
            Subjects
        </Page>
    )
}
