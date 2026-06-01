import { createFileRoute, type ErrorRouteComponent, Outlet, useRouter } from '@tanstack/solid-router'
import { createRenderEffect, Match, on, Switch } from 'solid-js'
import { UnauthorizedError, UserType } from '~/api'
import LoadingPage from '~/components/pages/LoadingPage'
import { useLogoutRedirect } from '~/hooks/useAuthRedirect'
import { AuthenticationState, useAPI } from '~/providers/APIProvider'
import { nonNull } from '~/utils'
import { catchErrors } from '~/utils/error-component'

export const ADMIN_AUTHENTICATED_ROUTE_DEFAULTS = {
    errorComponent: catchErrors([UnauthorizedError, UnauthorizedRedirect]),
} satisfies {
    errorComponent: ErrorRouteComponent
}

export const Route = createFileRoute('/_adminAuthenticated')({
    ...ADMIN_AUTHENTICATED_ROUTE_DEFAULTS,
    beforeLoad: async ({ context }) => {
        await context.authState
    },
    component: AdminAuthenticatedLayout,
})

function AdminAuthenticatedLayout() {
    const api = useAPI()

    useAdminLogoutRedirect()
    useNonAdminCrossRedirect()

    return (
        <Switch>
            <Match
                when={
                    api.authState() === AuthenticationState.LoggedIn && nonNull(api.client.user).type === UserType.ADMIN
                }
            >
                <Outlet />
            </Match>
            <Match when={api.authState() === AuthenticationState.Loading}>
                <LoadingPage debugName="AdminAuthenticatedLayout" />
            </Match>
        </Switch>
    )
}

function UnauthorizedRedirect() {
    useAdminLogoutRedirect()
    return null
}

const useAdminLogoutRedirect = () => useLogoutRedirect('/login')

/**
 * Redirect non-admin users who land on a management route to the user dashboard.
 */
function useNonAdminCrossRedirect() {
    const api = useAPI()
    const navigate = useRouter().navigate

    createRenderEffect(
        on(api.authState, state => {
            if (state !== AuthenticationState.LoggedIn) return
            if (nonNull(api.client.user).type !== UserType.ADMIN) {
                navigate({ to: '/', replace: true })
            }
        }),
    )
}
