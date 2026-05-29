import { createFileRoute, Outlet, useMatchRoute } from '@tanstack/solid-router'
import DashboardLayout from '~/components/layout/DashboardLayout'
import { getAdminNav } from '~/components/layout/navEntries'
import { BaseSubjectDisplayContext, SubjectDisplayContextProvider } from '~/components/subjects/SubjectDisplayContext'
import { UserDisplayContextProvider, useUserDisplayContext } from '~/components/users/UserDisplayContext'
import { useAPI } from '~/providers/APIProvider'
import type { UserType } from '~/api'

export const Route = createFileRoute('/_adminAuthenticated/manage')({
    component: RouteComponent,
})

function RouteComponent() {
    const { client } = useAPI()
    const userDisplayContext = useUserDisplayContext()
    const matchRoute = useMatchRoute()

    const userIdRouteMatch = matchRoute({ to: '/manage/users/$userId' })
    const isOnUserRouteForType = (type: UserType) => {
        const match = userIdRouteMatch()
        if (!match) return false
        const userId = match.userId
        return client.users.resolve(Number(userId))?.type === type
    }

    return (
        <DashboardLayout entries={getAdminNav(isOnUserRouteForType)}>
            <SubjectDisplayContextProvider
                value={{
                    editable: true,
                    createLinkProps: BaseSubjectDisplayContext.createLinkProps,
                    editLinkProps: BaseSubjectDisplayContext.editLinkProps,
                    viewLinkProps: (enrollmentId, subjectId, tab) => ({
                        to: '/manage/subjects/$subjectId',
                        params: { subjectId },
                        search: { enrollment_id: enrollmentId, tab },
                    }),
                }}
            >
                <UserDisplayContextProvider
                    value={{
                        ...userDisplayContext,
                        editable: true,
                        createLinkProps: type => ({
                            to: '/manage/users/$userId',
                            params: { userId: 'new' },
                            search: { type },
                        }),
                        editLinkProps: userId => ({
                            to: '/manage/users/$userId',
                            params: { userId },
                        }),
                        viewLinkProps: userId => ({
                            to: '/manage/users/$userId',
                            params: { userId },
                        }),
                    }}
                >
                    <Outlet />
                </UserDisplayContextProvider>
            </SubjectDisplayContextProvider>
        </DashboardLayout>
    )
}
