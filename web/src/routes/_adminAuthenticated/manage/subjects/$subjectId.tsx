import { SubjectTag } from '@bodin2/electives-common/proto/api'
import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { createMemo, createSignal, Show } from 'solid-js'
import { type AdminSubjectPatch, ConflictError, type RawSubject, Subject } from '../../../../api'
import Page from '../../../../components/Page'
import SubjectAdminEnrollmentActions from '../../../../components/subjects/SubjectAdminEnrollmentActions'
import SubjectInfo from '../../../../components/subjects/SubjectInfo'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { simpleXXHash31 } from '../../../../utils/xxhash'
import { Route as SubjectsRoute } from '../index'
import type { PatchSetterKey } from '../../../../components/subjects/SubjectDisplayContext'

export const Route = createFileRoute('/_adminAuthenticated/manage/subjects/$subjectId')({
    component: RouteComponent,
    params: {
        parse: (raw): { subjectId: string | number } => ({
            subjectId: raw.subjectId,
        }),
    },
    validateSearch: (search): { elective_id?: number } => ({
        elective_id: search?.elective_id != null ? Number(search?.elective_id) : undefined,
    }),
    loader: async ({ params: { subjectId }, context: { client } }) => {
        const allElectives = await client.electives.fetchAll()

        if (isNewRoute(subjectId)) {
            return {
                subject: null,
                electives: allElectives,
                allElectives,
            }
        }

        const subjectIdNum = Number(subjectId)
        const [subject, electiveIds] = await Promise.all([
            client.subjects.admin.fetch(subjectIdNum),
            client.subjects.admin.fetchElectiveIds(subjectIdNum),
        ])

        const electives = allElectives.filter(e => electiveIds.includes(e.id))

        return {
            subject,
            electives,
            allElectives,
        }
    },
})

const isNewRoute = (subjectId: string | number) => subjectId === 'new'

function RouteComponent() {
    const params = Route.useParams()
    const search = Route.useSearch()
    const data = Route.useLoaderData()
    const navigate = Route.useNavigate()

    const isNew = () => isNewRoute(params().subjectId)

    const { client } = useAPI()
    const { string } = useI18n()
    const router = useRouter()

    // Lifted state for the subject
    const [subjectData, setSubjectData] = createSignal<RawSubject>(
        data().subject?.toJSON() ?? {
            id: -1,
            name: string.NEW_SUBJECT_NAME(),
            description: string.NEW_SUBJECT_DESCRIPTION(),
            code: string.NEW_SUBJECT_CODE(),
            tag: SubjectTag.UNRECOGNIZED,
            location: string.NEW_SUBJECT_LOCATION(),
            capacity: 0,
            teachers: [],
        },
    )

    const subject = createMemo(() => new Subject(subjectData()))
    const elective = createMemo(() => {
        const electiveId = search().elective_id
        if (electiveId === undefined) return undefined
        return data().electives.find(e => e.id === electiveId)
    })

    const invalidate = () => router.invalidate({ filter: r => r.id === Route.id || r.id === SubjectsRoute.id })

    const handleEdit = async (key: string, val: unknown, patchKey?: PatchSetterKey) => {
        if (isNew()) {
            // Update signal with a NEW object to trigger re-render
            setSubjectData({ ...subjectData(), [key]: val })
        } else {
            const patch: AdminSubjectPatch = {
                teachers: [],
                patchDescription: false,
                patchCode: false,
                patchLocation: false,
                patchTeamId: false,
                patchTeachers: false,
                patchThumbnailUrl: false,
                patchImageUrl: false,
            }

            // @ts-expect-error
            patch[key as keyof AdminSubjectPatch] = val ?? undefined
            if (patchKey) patch[patchKey] = true

            try {
                await client.subjects.admin.patch(subject().id, patch)
                // Update signal with a NEW object to trigger re-render
                setSubjectData({ ...subjectData(), [key]: val })
                await invalidate()
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
            }
        }
    }

    const handleCreate = async () => {
        const s = subjectData()

        // Insane code
        while (true) {
            if (s.tag === SubjectTag.UNRECOGNIZED) {
                alert('Please set a valid subject category before creating the subject')
                break
            }

            if (s.id < 0)
                s.id = simpleXXHash31(
                    `${s.name}:${s.code}:${performance.now()}`,
                    Math.floor(Math.random() * 0x7fffffff),
                )

            try {
                await client.subjects.admin.put(s.id, s)
                await client.subjects.admin.fetchAll({ force: true })
                await invalidate()

                navigate({
                    params: { subjectId: s.id },
                    replace: true,
                })

                break
            } catch (e) {
                if (e instanceof ConflictError) continue

                console.error(e)
                alert(string.ERROR_CREATE_SUBJECT_FAILED({ error: String(e) }))
                break
            }
        }
    }

    return (
        <Page name={isNew() ? string.CREATE_SUBJECT() : subject().name} allowBacking leading={null} trailing={null}>
            <SubjectInfo
                subject={subject()}
                elective={elective()}
                user={client.user ?? undefined}
                editable
                creating={isNew()}
                onEdit={handleEdit}
                onSave={isNew() ? handleCreate : undefined}
                extraActions={props => (
                    <Show when={!isNew()}>
                        <SubjectAdminEnrollmentActions
                            subject={props.subject}
                            elective={props.elective}
                            allElectives={data().allElectives}
                            addedElectives={data().electives}
                            setElectiveId={id =>
                                navigate({
                                    search: prev => ({ ...prev, elective_id: id }),
                                    replace: true,
                                })
                            }
                            onInvalidate={invalidate}
                        />
                    </Show>
                )}
            />
        </Page>
    )
}
