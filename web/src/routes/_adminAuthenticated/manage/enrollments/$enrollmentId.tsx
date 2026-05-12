import { createQuery } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createMemo, createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { ConflictError, Enrollment, NotFoundError, type RawEnrollment } from '~/api'
import { ConfirmDialog } from '~/components/dialogs/base/ConfirmDialog'
import EnrollmentInfo from '~/components/enrollments/EnrollmentInfo'
import Page from '~/components/Page'
import NotFoundPage from '~/components/pages/NotFoundPage'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentQueryOptions, enrollmentSubjectsQueryOptions } from '~/queries/enrollments'
import { queryClient } from '~/queries/queryClient'
import { catchErrors } from '~/utils/error-component'
import { simpleXXHash31 } from '~/utils/xxhash'
import type { AdminEnrollmentPatch } from '~/api/types'

export const Route = createFileRoute('/_adminAuthenticated/manage/enrollments/$enrollmentId')({
    validateSearch: (search: Record<string, unknown>): { tab?: string } => ({
        tab: (search.tab as string) || undefined,
    }),
    component: RouteComponent,
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    params: {
        parse: (raw): { enrollmentId: string | number } => ({
            enrollmentId: raw.enrollmentId,
        }),
    },
    loader: async ({ params: { enrollmentId }, context: { client, queryClient } }) => {
        if (isNewRoute(enrollmentId)) return

        const enrollmentIdNum = Number(enrollmentId)
        await Promise.all([
            queryClient.ensureQueryData(enrollmentQueryOptions(client, enrollmentIdNum)),
            queryClient.ensureQueryData(enrollmentSubjectsQueryOptions(client, enrollmentIdNum)),
        ])
    },
})

const isNewRoute = (id: string | number) => id === 'new'

function RouteComponent() {
    const params = Route.useParams()
    const navigate = Route.useNavigate()

    const isNew = () => isNewRoute(params().enrollmentId)

    const { client } = useAPI()
    const { string } = useI18n()

    const enrollmentQuery = createQuery(() => ({
        ...enrollmentQueryOptions(client, Number(params().enrollmentId)),
        enabled: !isNew(),
    }))

    const [enrollmentData, setEnrollmentData] = createSignal<RawEnrollment>(
        enrollmentQuery.data?.toJSON() ?? {
            id: -1,
            name: string.NEW_ENROLLMENT_NAME(),
            groupId: undefined,
        },
    )

    const enrollment = createMemo(() => new Enrollment(client, enrollmentData()))

    const [confirmDeleteOpen, setConfirmDeleteOpen] = createSignal(false)

    const handleEdit = async (key: string, val: unknown, patchKey?: string) => {
        if (isNew()) {
            setEnrollmentData({ ...enrollmentData(), [key]: val })
        } else {
            const patch: AdminEnrollmentPatch = {
                patchStartDate: false,
                patchEndDate: false,
                patchGroupId: false,
            }

            // @ts-expect-error
            patch[key as keyof AdminEnrollmentPatch] = val ?? undefined
            if (patchKey) {
                // @ts-expect-error
                patch[patchKey as keyof AdminEnrollmentPatch] = true
            }

            try {
                await client.enrollments.admin.patch(enrollment().id, patch)
                setEnrollmentData({ ...enrollmentData(), [key]: val })
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
            }
        }
    }

    const handleCreate = async () => {
        const s = enrollmentData()

        while (true) {
            if (s.id < 0)
                s.id = simpleXXHash31(`${s.name}:${performance.now()}`, Math.floor(Math.random() * 0x7fffffff))

            try {
                await client.enrollments.admin.put(s.id, s)

                navigate({
                    params: { enrollmentId: s.id },
                    replace: true,
                })

                break
            } catch (e) {
                if (e instanceof ConflictError) continue

                console.error(e)
                alert(`Failed to create enrollment: ${String(e)}`)
                break
            }
        }
    }

    const doDelete = async () => {
        await client.enrollments.admin.delete(enrollment().id)
        await queryClient.removeQueries({ queryKey: ['enrollments', enrollment().id] })
        await navigate({ to: '..' })
    }

    const handleDelete = () => {
        setConfirmDeleteOpen(true)
    }

    return (
        <Page
            name={isNew() ? string.CREATE_ENROLLMENT() : enrollment().name}
            allowBacking
            leading={null}
            trailing={null}
        >
            <EnrollmentInfo
                creating={isNew()}
                enrollment={enrollment()}
                editable
                onEdit={handleEdit}
                onSave={isNew() ? handleCreate : undefined}
                onDelete={handleDelete}
            />
            <Portal>
                <ConfirmDialog
                    open={confirmDeleteOpen()}
                    variant="danger"
                    closedBy="any"
                    onCancel={() => setConfirmDeleteOpen(false)}
                    onConfirm={doDelete}
                    confirmText={string.DELETE_ENROLLMENT()}
                    headline={string.DELETE_ENROLLMENT()}
                >
                    <p>{string.CONFIRM_DELETE_ENROLLMENT({ name: <strong>{enrollment().name}</strong> })}</p>
                </ConfirmDialog>
            </Portal>
        </Page>
    )
}
