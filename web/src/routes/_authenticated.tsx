import { createFileRoute, type ErrorRouteComponent, Outlet, useRouter } from '@tanstack/solid-router'
import { createRenderEffect, Match, on, Switch } from 'solid-js'
import { UnauthorizedError } from '~/api'
import DashboardLayout from '~/components/layout/DashboardLayout'
import { getUserNav } from '~/components/layout/navEntries'
import LoadingPage from '~/components/pages/LoadingPage'
import {
    type UserDisplayContext,
    UserDisplayContextProvider,
    useUserDisplayContext,
} from '~/components/users/UserDisplayContext'
import { useLogoutRedirect } from '~/hooks/useAuthRedirect'
import { AuthenticationState, useAPI } from '~/providers/APIProvider'
import { nonNull } from '~/utils'
import { catchErrors } from '~/utils/error-component'

export const AUTHENTICATED_ROUTE_DEFAULTS = {
    errorComponent: catchErrors([UnauthorizedError, UnauthorizedRedirect]),
} satisfies {
    errorComponent: ErrorRouteComponent
}

export const Route = createFileRoute('/_authenticated')({
    ...AUTHENTICATED_ROUTE_DEFAULTS,
    beforeLoad: async ({ context }) => {
        await context.authState
    },
    component: AuthenticatedLayout,
})

function AuthenticatedLayout() {
    const api = useAPI()
    const userDisplayContext = useUserDisplayContext()

    const udcValue = (): UserDisplayContext => {
        if (api.client.user?.isTeacher())
            return {
                ...userDisplayContext,
                viewLinkProps: userId => ({
                    to: '/users/$userId',
                    params: { userId },
                }),
            }

        return userDisplayContext
    }

    useUserLogoutRedirect()
    useAdminCrossRedirect()

    return (
        <Switch>
            <Match
                when={
                    api.authState() === AuthenticationState.LoggedIn &&
                    !nonNull(api.client.user).isAdmin() &&
                    api.client.user
                }
            >
                {user => (
                    <UserDisplayContextProvider value={udcValue()}>
                        <DashboardLayout entries={getUserNav(user().type)}>
                            <Outlet />
                        </DashboardLayout>
                    </UserDisplayContextProvider>
                )}
            </Match>
            <Match when={api.authState() === AuthenticationState.Loading}>
                <LoadingPage debugName="AuthenticatedLayout" />
            </Match>
        </Switch>
    )
}

function UnauthorizedRedirect() {
    useUserLogoutRedirect()
    return null
}

const useUserLogoutRedirect = () => useLogoutRedirect('/login')

/**
 * Redirect admins who land on a user-area route to the management dashboard.
 */
function useAdminCrossRedirect() {
    const api = useAPI()
    const navigate = useRouter().navigate

    createRenderEffect(
        on(api.authState, state => {
            if (state !== AuthenticationState.LoggedIn) return
            if (api.client.user?.isAdmin()) {
                navigate({ to: '/manage', replace: true })
            }
        }),
    )
}
