import Logger from '@bodin2/electives-common/Logger'
import { SubjectTag, type UserType } from '@bodin2/electives-common/proto/api'
import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { createEffect, createMemo, createSignal, Show } from 'solid-js'
import {
    type AdminSubjectPatch,
    ConflictError,
    type Enrollment,
    NotFoundError,
    type RawSubject,
    Subject,
    type User,
} from '../../../../api'
import AddStudentToSubjectButton from '../../../../components/buttons/AddStudentToSubjectButton'
import AddTeacherToSubjectButton from '../../../../components/buttons/AddTeacherToSubjectButton'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import { HStack, VStack } from '../../../../components/Stack'
import SubjectAdminEnrollmentActions from '../../../../components/subjects/SubjectAdminEnrollmentActions'
import SubjectInfo from '../../../../components/subjects/SubjectInfo'
import { useRetryableSubscription } from '../../../../hooks/useRetryableSubscription'
import useSubjectFull from '../../../../hooks/useSubjectFull'
import { useAPI } from '../../../../providers/APIProvider'
import { useEnrollmentCounts } from '../../../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { enrollmentsQueryOptions } from '../../../../queries/enrollments'
import {
    adminSubjectEnrollmentIdsQueryOptions,
    subjectEnrolledCountQueryOptions,
    subjectMembersQueryOptions,
    subjectQueryOptions,
} from '../../../../queries/subjects'
import { nonNull } from '../../../../utils'
import { catchErrors } from '../../../../utils/error-component'
import { simpleXXHash31 } from '../../../../utils/xxhash'
import styles from './$subjectId.module.css'
import type { PatchSetterKey } from '../../../../components/subjects/SubjectDisplayContext'

export const Route = createFileRoute('/_adminAuthenticated/manage/subjects/$subjectId')({
    component: RouteComponent,
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    params: {
        parse: (raw): { subjectId: string | number } => ({
            subjectId: raw.subjectId,
        }),
    },
    validateSearch: (search: Record<string, unknown>): { enrollment_id?: number; tab?: string } => ({
        enrollment_id: search?.enrollment_id != null ? Number(search?.enrollment_id) : undefined,
        tab: (search.tab as string) || undefined,
    }),
    loaderDeps: ({ search: { enrollment_id } }) => ({ enrollmentId: enrollment_id }),
    loader: async ({ params: { subjectId }, context: { client, queryClient }, deps: { enrollmentId } }) => {
        await queryClient.ensureQueryData(enrollmentsQueryOptions(client))

        if (isNewRoute(subjectId)) return

        const subjectIdNum = Number(subjectId)
        const enrollmentIds = await queryClient.ensureQueryData(
            adminSubjectEnrollmentIdsQueryOptions(client, subjectIdNum),
        )

        // Speculative loading
        enrollmentId = (enrollmentId ?? enrollmentIds.length === 1) ? enrollmentIds[0] : undefined

        const promises: Promise<unknown>[] = []

        if (enrollmentId !== undefined) {
            promises.push(
                queryClient.ensureQueryData(
                    subjectQueryOptions(client, { enrollmentId, subjectId: subjectIdNum, withDescription: true }),
                ),
                queryClient.ensureQueryData(
                    subjectMembersQueryOptions(client, { enrollmentId, subjectId: subjectIdNum }),
                ),
                queryClient.ensureQueryData(
                    subjectEnrolledCountQueryOptions(client, { enrollmentId, subjectId: subjectIdNum }),
                ),
            )
        } else {
            promises.push(
                queryClient.ensureQueryData({
                    queryKey: ['admin', 'subjects', subjectIdNum] as const,
                    queryFn: () => client.subjects.admin.fetch(subjectIdNum),
                }),
            )
        }

        await Promise.all(promises)
    },
})

const isNewRoute = (subjectId: string | number) => subjectId === 'new'
const log = new Logger('routes/admin/manage/subjects/$subjectId')

function RouteComponent() {
    const params = Route.useParams()
    const search = Route.useSearch()
    const navigate = Route.useNavigate()

    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()
    const enrollment = useEnrollmentCounts()

    const isNew = () => isNewRoute(params().subjectId)
    const enrollmentId = () => search().enrollment_id
    const subjectIdNum = () => (isNew() ? undefined : Number(params().subjectId))

    const enrollmentsQuery = createQuery(() => enrollmentsQueryOptions(client))
    const allEnrollments = () => enrollmentsQuery.data ?? []

    const enrollmentIdsQuery = createQuery(() => {
        const id = subjectIdNum()
        return {
            ...adminSubjectEnrollmentIdsQueryOptions(client, id ?? -1),
            enabled: !isNew() && id !== undefined,
        }
    })

    const subjectByEnrollmentQuery = createQuery(() => {
        const eid = enrollmentId()
        const sid = subjectIdNum()
        return {
            ...subjectQueryOptions(client, {
                enrollmentId: eid ?? -1,
                subjectId: sid ?? -1,
                withDescription: true,
            }),
            enabled: !isNew() && eid !== undefined && sid !== undefined,
        }
    })

    const adminSubjectQuery = createQuery(() => {
        const sid = subjectIdNum()
        return {
            queryKey: ['admin', 'subjects', sid ?? -1] as const,
            queryFn: () => client.subjects.admin.fetch(nonNull(sid, 'Subject ID not available')),
            enabled: !isNew() && enrollmentId() === undefined && sid !== undefined,
        }
    })

    const membersQuery = createQuery(() => {
        const eid = enrollmentId()
        const sid = subjectIdNum()
        return {
            ...subjectMembersQueryOptions(client, {
                enrollmentId: eid ?? -1,
                subjectId: sid ?? -1,
            }),
            enabled: !isNew() && eid !== undefined && sid !== undefined,
        }
    })

    const loadedSubject = () => {
        if (enrollmentId() !== undefined) return subjectByEnrollmentQuery.data
        return adminSubjectQuery.data
    }

    const enrollments = () => {
        const ids = enrollmentIdsQuery.data ?? []
        return allEnrollments().filter(e => ids.includes(e.id))
    }

    const teachers = () => membersQuery.data?.teachers ?? []

    createEffect(() => {
        const eid = enrollmentId()
        if (eid !== undefined) {
            enrollment.initializeCounts(eid, client.enrollments.resolveAllEnrolledCounts(eid))
        }
    })

    const [subjectData, setSubjectData] = createSignal<RawSubject>(
        loadedSubject()?.toJSON() ?? {
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

    const subject = createMemo(() => new Subject(client, subjectData()))
    const enrollment_ = createMemo(() => {
        const eid = enrollmentId()
        if (eid === undefined) return undefined
        return allEnrollments().find(e => e.id === eid)
    })

    // WebSocket subscription for real-time updates
    useRetryableSubscription(
        () => {
            const eid = enrollmentId()
            if (eid === undefined) return

            client.gateway.subscribeToEnrollment(eid, [subject().id].filter(Boolean) as number[])
        },
        () => {
            const eid = enrollmentId()
            if (eid === undefined) return

            if (client.isGatewayConnected()) {
                client.gateway.subscribeToEnrollment(eid, [])
            } else log.warn('WebSocket not connected, skipping unsubscription')
        },
    )

    const currentTeacherIds = () => {
        return teachers().map(t => t.id)
    }

    createEffect(() => {
        const s = loadedSubject()
        if (s) setSubjectData(s.toJSON())
    })

    const invalidate = () =>
        Promise.all([
            qc.invalidateQueries({ queryKey: ['admin', 'subjects'] }),
            qc.invalidateQueries({ queryKey: ['subjects'] }),
            qc.invalidateQueries({ queryKey: ['enrollments'] }),
        ])

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
                patchGroupId: false,
                patchTeachers: false,
                patchThumbnailUrl: false,
                patchImageUrl: false,
            }

            // @ts-expect-error
            patch[key as keyof AdminSubjectPatch] = val ?? undefined
            if (patchKey) patch[patchKey] = true

            try {
                await client.subjects.admin.patch(subject().id, patch)
                // Update the cached instance directly for immediate feedback
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
                if (e instanceof ConflictError) {
                    s.id = -1
                    continue
                }

                console.error(e)
                alert(string.ERROR_CREATE_SUBJECT_FAILED({ error: String(e) }))
                break
            }
        }
    }

    const invalidateUser = (_type: UserType) =>
        Promise.all([
            qc.invalidateQueries({ queryKey: ['subjects'] }),
            qc.invalidateQueries({ queryKey: ['users'] }),
            qc.invalidateQueries({ queryKey: ['admin', 'students'] }),
            qc.invalidateQueries({ queryKey: ['admin', 'teachers'] }),
        ])

    const handleStudentRemove = async (stud: User) => {
        const en = nonNull(enrollment_())

        await client.selections.delete(stud.id, en.id)
        enrollment.setCount(en.id, subject().id, current => current - 1)

        await invalidateUser(stud.type)
    }

    const handleTeacherRemove = async (teach: User) => {
        const en = nonNull(enrollment_())

        await client.subjects.admin.patch(subject().id, {
            teachers: currentTeacherIds().filter(id => id !== teach.id),
            enrollmentId: en.id,
            patchTeachers: true,
            patchCode: false,
            patchDescription: false,
            patchImageUrl: false,
            patchLocation: false,
            patchGroupId: false,
            patchThumbnailUrl: false,
        })

        enrollment.bumpVersion(en.id)

        await invalidateUser(teach.type)
        await qc.invalidateQueries({ queryKey: ['subjects'] })
    }

    return (
        <Page name={isNew() ? string.CREATE_SUBJECT() : subject().name} allowBacking leading={null} trailing={null}>
            <SubjectInfo
                subject={subject()}
                enrollment={enrollment_()}
                teachers={teachers()}
                user={client.user ?? undefined}
                editable
                creating={isNew()}
                onEdit={handleEdit}
                onSave={isNew() ? handleCreate : undefined}
                onStudentRemove={handleStudentRemove}
                onTeacherRemove={handleTeacherRemove}
                extraActions={props => (
                    <ExtraActions
                        subject={props.subject}
                        enrollment={props.enrollment}
                        isNew={isNew()}
                        enrollments={enrollments()}
                        allEnrollments={allEnrollments()}
                        onInvalidate={invalidate}
                    />
                )}
            />
        </Page>
    )
}

function ExtraActions(props: {
    subject: Subject
    enrollment?: Enrollment
    isNew: boolean
    enrollments: Enrollment[]
    allEnrollments: Enrollment[]
    onInvalidate: () => unknown
}) {
    const navigate = Route.useNavigate()

    const isFull = useSubjectFull(
        () => props.subject,
        () => nonNull(props.enrollment),
    )

    return (
        <Show when={!props.isNew}>
            <VStack>
                <SubjectAdminEnrollmentActions
                    subject={props.subject}
                    enrollment={props.enrollment}
                    allEnrollments={props.allEnrollments}
                    addedEnrollments={props.enrollments}
                    setEnrollmentId={id =>
                        navigate({
                            search: prev => ({ ...prev, enrollment_id: id }),
                            replace: true,
                        })
                    }
                    onInvalidate={props.onInvalidate}
                />
                <HStack grow wrap gap={8} class={styles.actionButton}>
                    <AddStudentToSubjectButton
                        variant="tonal"
                        class={styles.subActionButton}
                        enrollmentId={props.enrollment?.id}
                        subjectId={props.subject.id}
                        disabled={!props.enrollment || isFull()}
                    />
                    <AddTeacherToSubjectButton
                        variant="tonal"
                        class={styles.subActionButton}
                        subjectId={props.subject.id}
                        enrollmentId={props.enrollment?.id}
                        disabled={!props.enrollment}
                        onInvalidate={props.onInvalidate}
                    />
                </HStack>
            </VStack>
        </Show>
    )
}
