import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createMemo, createSignal } from 'solid-js'
import { Portal } from 'solid-js/web'
import { ConflictError, Elective, type RawElective } from '../../../../api'
import { ConfirmDialog } from '../../../../components/dialogs/base/ConfirmDialog'
import ElectiveInfo from '../../../../components/electives/ElectiveInfo'
import Page from '../../../../components/Page'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { electiveQueryOptions, electiveSubjectsQueryOptions } from '../../../../queries/electives'
import { simpleXXHash31 } from '../../../../utils/xxhash'
import type { AdminElectivePatch } from '../../../../api/types'

export const Route = createFileRoute('/_adminAuthenticated/manage/enrollments/$enrollmentId')({
    validateSearch: (search: Record<string, unknown>): { tab?: string } => ({
        tab: (search.tab as string) || undefined,
    }),
    component: RouteComponent,
    params: {
        parse: (raw): { enrollmentId: string | number } => ({
            enrollmentId: raw.enrollmentId,
        }),
    },
    loader: async ({ params: { enrollmentId }, context: { client, queryClient } }) => {
        if (isNewRoute(enrollmentId)) return

        const electiveIdNum = Number(enrollmentId)
        await Promise.all([
            queryClient.ensureQueryData(electiveQueryOptions(client, electiveIdNum)),
            queryClient.ensureQueryData(electiveSubjectsQueryOptions(client, electiveIdNum)),
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
    const qc = useQueryClient()

    const electiveQuery = createQuery(() => ({
        ...electiveQueryOptions(client, Number(params().enrollmentId)),
        enabled: !isNew(),
    }))

    const [electiveData, setElectiveData] = createSignal<RawElective>(
        electiveQuery.data?.toJSON() ?? {
            id: -1,
            name: string.NEW_ENROLLMENT_NAME(),
            teamId: undefined,
        },
    )

    const elective = createMemo(() => new Elective(client, electiveData()))

    const [confirmDeleteOpen, setConfirmDeleteOpen] = createSignal(false)

    const invalidate = () => qc.invalidateQueries({ queryKey: ['electives'] })

    const handleEdit = async (key: string, val: unknown, patchKey?: string) => {
        if (isNew()) {
            setElectiveData({ ...electiveData(), [key]: val })
        } else {
            const patch: AdminElectivePatch = {
                patchStartDate: false,
                patchEndDate: false,
                patchTeamId: false,
            }

            // @ts-expect-error
            patch[key as keyof AdminElectivePatch] = val ?? undefined
            if (patchKey) {
                // @ts-expect-error
                patch[patchKey as keyof AdminElectivePatch] = true
            }

            try {
                await client.electives.admin.patch(elective().id, patch)
                setElectiveData({ ...electiveData(), [key]: val })
                await invalidate()
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
            }
        }
    }

    const handleCreate = async () => {
        const s = electiveData()

        while (true) {
            if (s.id < 0)
                s.id = simpleXXHash31(`${s.name}:${performance.now()}`, Math.floor(Math.random() * 0x7fffffff))

            try {
                await client.electives.admin.put(s.id, s)
                await client.electives.fetchAll({ force: true })
                await invalidate()

                navigate({
                    params: { enrollmentId: s.id },
                    replace: true,
                })

                break
            } catch (e) {
                if (e instanceof ConflictError) continue

                console.error(e)
                alert(`Failed to create elective: ${String(e)}`)
                break
            }
        }
    }

    const doDelete = async () => {
        await client.electives.admin.delete(elective().id)
        await qc.removeQueries({ queryKey: ['electives', elective().id] })
        await invalidate()
        await navigate({ to: '..' })
    }

    const handleDelete = () => {
        setConfirmDeleteOpen(true)
    }

    return (
        <Page name={isNew() ? string.CREATE_ENROLLMENT() : elective().name} allowBacking leading={null} trailing={null}>
            <ElectiveInfo
                creating={isNew()}
                elective={elective()}
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
                    confirmText={string.DELETE_USER()}
                    headline={string.DELETE_USER()}
                >
                    <p>{string.CONFIRM_DELETE_ENROLLMENT({ name: <strong>{elective().name}</strong> })}</p>
                </ConfirmDialog>
            </Portal>
        </Page>
    )
}
