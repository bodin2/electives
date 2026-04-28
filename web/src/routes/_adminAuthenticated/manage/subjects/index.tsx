import DeleteOutlineIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../../../components/Button'
import { Dialog } from '../../../../components/Dialog'
import LinkButton from '../../../../components/LinkButton'
import Page from '../../../../components/Page'
import { HStack } from '../../../../components/Stack'
import { BaseSubjectDisplayContext } from '../../../../components/subjects/SubjectDisplayContext'
import SubjectList from '../../../../components/subjects/SubjectList'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { Route as SubjectIdRoute } from './$subjectId'
import type { Subject } from '../../../../api'

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
    const [deletingSubject, setDeletingSubject] = createSignal<Subject | undefined>(undefined)

    return (
        <Page name={string.SUBJECTS()} leading={null} trailing={null}>
            <SubjectList
                noRandom
                subjects={data().subjects}
                editable
                viewLinkProps={subjectId => BaseSubjectDisplayContext.editLinkProps(subjectId)}
                headerActions={
                    <LinkButton {...BaseSubjectDisplayContext.createLinkProps()} variant="filled" icon={PlusIcon}>
                        {string.CREATE_SUBJECT()}
                    </LinkButton>
                }
                itemActions={subject => (
                    <HStack gap={8}>
                        <LinkButton
                            {...BaseSubjectDisplayContext.editLinkProps(subject.id)}
                            variant="text"
                            iconType="only"
                            icon={PencilOutlineIcon}
                            aria-label={string.EDIT_SUBJECT()}
                        />
                        <Button
                            variant="tonal-error"
                            iconType="only"
                            icon={DeleteOutlineIcon}
                            aria-label={string.DELETE_SUBJECT()}
                            onClick={e => {
                                e.stopPropagation()
                                setDeletingSubject(subject)
                            }}
                        />
                    </HStack>
                )}
            />
            <Portal>
                <SubjectDeletionDialog deletingSubject={deletingSubject()} setDeletingSubject={setDeletingSubject} />
            </Portal>
        </Page>
    )
}

function SubjectDeletionDialog(props: {
    deletingSubject: Subject | undefined
    setDeletingSubject: (s: Subject | undefined) => void
}) {
    const { client } = useAPI()
    const { string } = useI18n()
    const router = useRouter()

    const handleDelete = async () => {
        const subject = props.deletingSubject
        if (!subject) return

        try {
            await client.subjects.admin.delete(subject.id)
            await router.invalidate({ filter: r => r.routeId === Route.id || r.routeId === SubjectIdRoute.id })
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_SUBJECT_FAILED())
        } finally {
            props.setDeletingSubject(undefined)
        }
    }

    return (
        <Dialog
            open={!!props.deletingSubject}
            closedBy="any"
            onClose={() => props.setDeletingSubject(undefined)}
            headline={string.DELETE_SUBJECT()}
            actions={
                <HStack slot="actions" gap={8}>
                    <Button variant="text" onClick={() => props.setDeletingSubject(undefined)}>
                        {string.CANCEL()}
                    </Button>
                    <Button variant="tonal-error" onClick={handleDelete}>
                        {string.DELETE_SUBJECT()}
                    </Button>
                </HStack>
            }
        >
            <p>{string.CONFIRM_DELETE_SUBJECT({ name: props.deletingSubject?.name ?? '' })}</p>
        </Dialog>
    )
}
