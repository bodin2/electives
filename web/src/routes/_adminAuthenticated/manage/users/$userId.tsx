import { createFileRoute, useRouter } from '@tanstack/solid-router'
import { batch, createEffect, createMemo, createSignal, on, onCleanup, onMount } from 'solid-js'
import { type AdminUserPatch, User, UserType } from '../../../../api'
import Page from '../../../../components/Page'
import {
    type UserData,
    type UserPatchSetterKey,
    useUserDisplayContext,
} from '../../../../components/users/UserDisplayContext'
import UserInfo from '../../../../components/users/UserInfo'
import { useAPI } from '../../../../providers/APIProvider'
import { useI18n } from '../../../../providers/I18nProvider'
import { Route as StudentsRoute } from '../students'
import { Route as TeachersRoute } from '../teachers'

type UserSearch = {
    type?: 'student' | 'teacher'
}

export const Route = createFileRoute('/_adminAuthenticated/manage/users/$userId')({
    validateSearch: (search: Record<string, unknown>): UserSearch => {
        return {
            type: (search.type as 'student' | 'teacher') || undefined,
        }
    },
    component: RouteComponent,
    loader: async ({ params: { userId }, context: { client } }) => {
        const teams = await client.teams.fetchAll().catch(() => [])

        if (isNewRoute(userId)) {
            return { user: null, teams }
        }

        const userIdNum = Number(userId)
        const user = await client.users.fetch(userIdNum).catch(() => null)

        return { user, teams }
    },
})

const isNewRoute = (userId: string) => userId === 'new'

function RouteComponent() {
    const params = Route.useParams()
    const search = Route.useSearch()
    const data = Route.useLoaderData()
    const navigate = Route.useNavigate()

    const isNew = () => isNewRoute(params().userId)

    const { client } = useAPI()
    const { string } = useI18n()
    const router = useRouter()
    const displayContext = useUserDisplayContext()

    const initialType = () => {
        if (search().type === 'teacher') return UserType.TEACHER
        return UserType.STUDENT
    }

    const [userData, setUserData] = createSignal<UserData>(
        data().user?.toJSON() ?? {
            id: -1,
            firstName: string.NEW_USER_FIRST_NAME(),
            type: initialType(),
            teams: [],
        },
    )

    const [modifiedFields, setModifiedFields] = createSignal<Set<string>>(new Set())

    const user = createMemo(() => new User(userData()))

    createEffect(() => {
        displayContext.setEdited(modifiedFields().size > 0)
    })

    createEffect(
        on(
            () => data().user,
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
        if (!isNew()) return user().fullName

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

    const invalidate = async () => {
        await router.invalidate({
            filter: r => r.id === Route.id || r.id === TeachersRoute.id || r.id === StudentsRoute.id,
        })
    }

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
                patchMiddleName: modified.has('middleName') || modified.has('patchMiddleName'),
                patchAvatarUrl: modified.has('avatarUrl') || modified.has('patchAvatarUrl'),
                patchLastName: modified.has('lastName') || modified.has('patchLastName'),
                firstName: u.firstName,
                middleName: u.middleName,
                lastName: u.lastName,
                avatarUrl: u.avatarUrl || undefined,
                newPassword: modified.has('newPassword') ? u.newPassword : undefined,
            }

            try {
                await client.users.admin.patch(user().id, patch)
                setModifiedFields(new Set<string>())
                setUserData({ ...userData(), newPassword: '' })
                await invalidate()
            } catch (e) {
                console.error(e)
                alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
            }
        }
    }

    const handleDelete = async () => {
        await client.users.admin.delete(user().id)
        history.back()
        await invalidate()
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

    createEffect(() => {
        batch(() => {
            displayContext.setCreating(isNew())
            displayContext.setUser(user())
            displayContext.setUserData(userData())
        })
    })

    return (
        <Page name={title()} allowBacking leading={null} trailing={null}>
            <UserInfo initialType={initialType()} teams={data().teams} />
        </Page>
    )
}
