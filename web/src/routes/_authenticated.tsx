import { createFileRoute, type ErrorRouteComponent, Outlet } from '@tanstack/solid-router'
import { Match, Switch } from 'solid-js'
import { UnauthorizedError } from '~/api'
import DashboardLayout from '~/components/layout/DashboardLayout'
import { getUserNav } from '~/components/layout/navEntries'
import LoadingPage from '~/components/pages/LoadingPage'
import { useLogoutRedirect } from '~/hooks/useAuthRedirect'
import { AuthenticationState, TokenType, useAPI } from '~/providers/APIProvider'
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

    useUserLogoutRedirect()

    return (
        <Switch>
            <Match when={api.authState() === AuthenticationState.LoggedIn && api.tokenType() === TokenType.User}>
                <DashboardLayout entries={getUserNav(nonNull(api.client.user).type)}>
                    <Outlet />
                </DashboardLayout>
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

const useUserLogoutRedirect = () => useLogoutRedirect('/login', TokenType.User)
