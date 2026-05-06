import DeleteOutlineIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../../../components/Button'
import { ConfirmDialog } from '../../../../components/dialogs/base/ConfirmDialog'
import LinkButton from '../../../../components/LinkButton'
import Page from '../../../../components/Page'
import { SuspenseLoadingPage } from '../../../../components/pages/LoadingPage'
import { HStack } from '../../../../components/Stack'
import { useSubjectDisplayContext } from '../../../../components/subjects/SubjectDisplayContext'
import SubjectList from '../../../../components/subjects/SubjectList'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { adminSubjectsQueryOptions } from '../../../../queries/subjects'
import { nonNull } from '../../../../utils'
import styles from './index.module.css'
import type { Subject } from '../../../../api'

export const Route = createFileRoute('/_adminAuthenticated/manage/subjects/')({
    component: RouteComponent,
    loader: async ({ context: { client, queryClient } }) => {
        await queryClient.ensureQueryData(adminSubjectsQueryOptions(client))
    },
})

function RouteComponent() {
    const { string } = useI18n()
    const { client } = useAPI()
    const [deletingSubject, setDeletingSubject] = createSignal<Subject | undefined>(undefined)
    const subjectDisplayContext = useSubjectDisplayContext()

    const subjectsQuery = createQuery(() => adminSubjectsQueryOptions(client))

    return (
        <Page name={string.SUBJECTS()} leading={null} trailing={null}>
            <SuspenseLoadingPage>
                <SubjectList
                    noRandom
                    searchContainerClass={styles.adminSearchContainer}
                    subjects={nonNull(subjectsQuery.data)}
                    editable
                    viewLinkProps={subjectId => subjectDisplayContext.editLinkProps(subjectId)}
                    headerActions={
                        <LinkButton {...subjectDisplayContext.createLinkProps()} variant="filled" icon={PlusIcon}>
                            {string.CREATE_SUBJECT()}
                        </LinkButton>
                    }
                    itemActions={subject => (
                        <HStack gap={8}>
                            <LinkButton
                                {...subjectDisplayContext.editLinkProps(subject.id)}
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
            </SuspenseLoadingPage>
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
    const qc = useQueryClient()

    const handleDelete = async () => {
        const subject = props.deletingSubject
        if (!subject) return

        try {
            await client.subjects.admin.delete(subject.id)
            await qc.invalidateQueries({ queryKey: ['admin', 'subjects'] })
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_SUBJECT_FAILED())
        } finally {
            props.setDeletingSubject(undefined)
        }
    }

    return (
        <ConfirmDialog
            open={!!props.deletingSubject}
            variant="danger"
            closedBy="any"
            onCancel={() => props.setDeletingSubject(undefined)}
            onConfirm={handleDelete}
            confirmText={string.DELETE_SUBJECT()}
            headline={string.DELETE_SUBJECT()}
        >
            <p>{string.CONFIRM_DELETE_SUBJECT({ name: <strong>{props.deletingSubject?.name ?? ''}</strong> })}</p>
        </ConfirmDialog>
    )
}
