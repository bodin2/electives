import { createQuery, useQueryClient } from '@tanstack/solid-query'
import { createFileRoute } from '@tanstack/solid-router'
import { batch, createEffect, createMemo, createRenderEffect, createSignal, on, onCleanup, onMount } from 'solid-js'
import { Portal } from 'solid-js/web'
import { type AdminUserPatch, NotFoundError, User, UserType } from '../../../../api'
import { ConfirmDialog } from '../../../../components/dialogs/base/ConfirmDialog'
import Page from '../../../../components/Page'
import NotFoundPage from '../../../../components/pages/NotFoundPage'
import {
    type UserData,
    type UserPatchSetterKey,
    useUserDisplayContext,
} from '../../../../components/users/UserDisplayContext'
import UserInfo from '../../../../components/users/UserInfo'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { teamsQueryOptions } from '../../../../queries/teams'
import { userQueryOptions } from '../../../../queries/users'
import { catchErrors } from '../../../../utils/error-component'

type UserSearch = {
    type?: 'student' | 'teacher'
    tab?: string
}

export const Route = createFileRoute('/_adminAuthenticated/manage/users/$userId')({
    params: {
        parse: ({ userId }): { userId: string | number } => ({ userId }),
    },
    validateSearch: (search: Record<string, unknown>): UserSearch => {
        return {
            type: (search.type as 'student' | 'teacher') || undefined,
            tab: (search.tab as string) || undefined,
        }
    },
    component: RouteComponent,
    errorComponent: catchErrors([NotFoundError, NotFoundPage]),
    loader: async ({ params: { userId }, context: { client, queryClient } }) => {
        const promises: Promise<unknown>[] = [queryClient.ensureQueryData(teamsQueryOptions(client)).catch(() => [])]

        if (!isNewRoute(userId)) {
            const userIdNum = Number(userId)
            promises.push(queryClient.ensureQueryData(userQueryOptions(client, userIdNum)))
        }

        await Promise.all(promises)
    },
})

const isNewRoute = (userId: string | number) => userId === 'new'

function RouteComponent() {
    const params = Route.useParams()
    const search = Route.useSearch()
    const navigate = Route.useNavigate()

    const isNew = () => isNewRoute(params().userId)

    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()
    const displayContext = useUserDisplayContext()

    const [confirmDeleteOpen, setConfirmDeleteOpen] = createSignal(false)

    const teamsQuery = createQuery(() => teamsQueryOptions(client))
    const userQuery = createQuery(() => {
        const id = Number(params().userId)
        return {
            ...userQueryOptions(client, Number.isNaN(id) ? 0 : id),
            enabled: !isNew() && !Number.isNaN(id),
        }
    })

    const teams = () => teamsQuery.data ?? []
    const loadedUser = () => userQuery.data ?? null

    const initialType = () => {
        if (search().type === 'teacher') return UserType.TEACHER
        return UserType.STUDENT
    }

    const [userData, setUserData] = createSignal<UserData>(
        loadedUser()?.toJSON() ?? {
            id: -1,
            firstName: string.NEW_USER_FIRST_NAME(),
            type: initialType(),
            teams: [],
        },
    )

    const [modifiedFields, setModifiedFields] = createSignal<Set<string>>(new Set())

    const user = createMemo(() => new User(client, userData()))

    createEffect(() => {
        displayContext.setEdited(modifiedFields().size > 0)
    })

    createEffect(
        on(
            () => loadedUser(),
            u => {
                if (u) {
                    batch(() => {
                        setUserData(u.toJSON())
                        setModifiedFields(new Set<string>())
                    })
                } else if (isNew()) {
                    batch(() => {
                        setUserData({
                            id: -1,
                            firstName: string.NEW_USER_FIRST_NAME(),
                            type: initialType(),
                            teams: [],
                        })
                        setModifiedFields(new Set<string>())
                    })
                }
            },
        ),
    )

    onCleanup(() => {
        displayContext.setUserData(undefined)
    })

    const title = () => {
        if (!isNew()) return user().displayName

        switch (userData().type) {
            case UserType.STUDENT:
                return string.ADD_STUDENT()
            case UserType.TEACHER:
                return string.ADD_TEACHER()
        }
    }

    const handleEdit = (key: string, val: unknown, patchKey?: UserPatchSetterKey) => {
        setUserData({ ...userData(), [key]: val })
        if (patchKey) {
            setModifiedFields(new Set([...modifiedFields(), patchKey]))
        } else {
            setModifiedFields(new Set([...modifiedFields(), key]))
        }
    }

    const invalidate = (type: UserType, userId?: number) =>
        Promise.all([
            qc.invalidateQueries({
                queryKey: ['admin', type === UserType.STUDENT ? 'students' : 'teachers'],
            }),
            userId !== undefined ? qc.invalidateQueries({ queryKey: ['users', userId] }) : undefined,
        ])

    const handleSave = async () => {
        const u = userData()

        if (isNew()) {
            if (u.id < 0) return
            if (!u.newPassword) return

            try {
                await client.users.admin.put(u.id, {
                    user: u,
                    password: u.newPassword,
                    teamIds: u.teams?.map(t => t.id) ?? [],
                })

                displayContext.setUserData({ ...userData(), newPassword: '' })

                await invalidate(u.type, u.id)

                navigate({
                    params: { userId: u.id.toString() },
                    replace: true,
                })
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
            }
        } else {
            const modified = modifiedFields()
            const patch: AdminUserPatch = {
                teams: u.teams?.map(t => t.id) ?? [],
                patchTeams: modified.has('teams') || modified.has('patchTeams'),
                patchPrefix: modified.has('prefix') || modified.has('patchPrefix'),
                patchMiddleName: modified.has('middleName') || modified.has('patchMiddleName'),
                patchAvatarUrl: modified.has('avatarUrl') || modified.has('patchAvatarUrl'),
                patchLastName: modified.has('lastName') || modified.has('patchLastName'),
                firstName: u.firstName,
                prefix: u.prefix,
                middleName: u.middleName,
                lastName: u.lastName,
                avatarUrl: u.avatarUrl || undefined,
                newPassword: modified.has('newPassword') ? u.newPassword : undefined,
            }

            try {
                await client.users.admin.patch(user().id, patch)
                setModifiedFields(new Set<string>())
                setUserData({ ...userData(), newPassword: '' })
                await invalidate(userData().type, user().id)
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
            }
        }
    }

    const handleDelete = () => {
        setConfirmDeleteOpen(true)
    }

    const doDelete = async () => {
        try {
            const deletedUserId = user().id
            const deletedUserType = userData().type

            await client.users.admin.delete(deletedUserId)

            // Remove, not invalidate
            qc.removeQueries({ queryKey: ['users', deletedUserId] })

            await qc.invalidateQueries({
                queryKey: ['admin', deletedUserType === UserType.STUDENT ? 'students' : 'teachers'],
            })

            switch (deletedUserType) {
                case UserType.TEACHER:
                    await navigate({ to: '/manage/teachers', replace: true, search: { page: 1 } })
                    break
                case UserType.STUDENT:
                    await navigate({ to: '/manage/students', replace: true, search: { page: 1 } })
                    break
            }
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_FAILED({ error: String(e) }))
        } finally {
            setConfirmDeleteOpen(false)
        }
    }

    onMount(() => {
        batch(() => {
            displayContext.setOnDelete(handleDelete)
            displayContext.setOnEdit(handleEdit)
            displayContext.setOnSave(handleSave)
        })

        onCleanup(() => {
            batch(() => {
                displayContext.setUser(undefined)
                displayContext.setCreating(false)
                displayContext.setOnDelete(undefined)
                displayContext.setOnEdit(undefined)
                displayContext.setOnSave(undefined)
            })
        })
    })

    createRenderEffect(() => {
        batch(() => {
            displayContext.setCreating(isNew())
            displayContext.setUser(user())
            displayContext.setUserData(userData())
        })
    })

    return (
        <Page name={title()} allowBacking leading={null} trailing={null}>
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
                    <p>{string.CONFIRM_DELETE_USER({ name: <strong>{user().displayName}</strong> })}</p>
                </ConfirmDialog>
            </Portal>
            <UserInfo initialType={initialType()} teams={teams()} />
        </Page>
    )
}
