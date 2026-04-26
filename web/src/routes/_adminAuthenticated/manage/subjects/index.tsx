import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { Portal } from 'solid-js/web'
import { Button } from '../../../../components/Button'
import { Dialog } from '../../../../components/Dialog'
import Page from '../../../../components/Page'
import { HStack } from '../../../../components/Stack'
import { useSubjectDisplayContext } from '../../../../components/subjects/SubjectDisplayContext'
import SubjectList from '../../../../components/subjects/SubjectList'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { Route as SubjectIdRoute } from './$subjectId'

export const Route = createFileRoute('/_adminAuthenticated/manage/subjects/')({
    component: RouteComponent,
    loader: async ({ context: { client } }) => {
        const subjects = await client.subjects.admin.fetchAll()
        return { subjects }
    },
})

function RouteComponent() {
    const { string } = useI18n()
    const data = Route.useLoaderData()

    return (
        <Page name={string.SUBJECTS()} leading={null} trailing={null}>
            <SubjectList noRandom subjects={data().subjects} />
            <Portal>
                <SubjectDeletionDialog />
            </Portal>
        </Page>
    )
}

function SubjectDeletionDialog() {
    const ctx = useSubjectDisplayContext()
    const { client } = useAPI()
    const { string } = useI18n()
    const router = useRouter()

    const handleDelete = async () => {
        const subject = ctx.deletingSubject
        if (!subject) return

        try {
            await client.subjects.admin.delete(subject.id)
            await router.invalidate({ filter: r => r.id === Route.id || r.id === SubjectIdRoute.id })
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_SUBJECT_FAILED())
        } finally {
            ctx.setDeletingSubject(undefined)
        }
    }

    return (
        <Dialog
            open={!!ctx.deletingSubject}
            closedBy="any"
            onClose={() => ctx.setDeletingSubject(undefined)}
            headline={string.DELETE_SUBJECT()}
            actions={
                <HStack slot="actions" gap={8}>
                    <Button variant="text" onClick={() => ctx.setDeletingSubject(undefined)}>
                        {string.CANCEL()}
                    </Button>
                    <Button variant="tonal-error" onClick={handleDelete}>
                        {string.DELETE_SUBJECT()}
                    </Button>
                </HStack>
            }
        >
            <p>{string.CONFIRM_DELETE_SUBJECT({ name: ctx.deletingSubject?.name ?? '' })}</p>
        </Dialog>
    )
}
