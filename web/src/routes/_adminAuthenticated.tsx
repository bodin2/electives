import { createFileRoute, type ErrorRouteComponent, Outlet } from '@tanstack/solid-router'
import { Match, Switch } from 'solid-js'
import { UnauthorizedError } from '../api'
import LoadingPage from '../components/pages/LoadingPage'
import { useLogoutRedirect } from '../hooks/useAuthRedirect'
import { AuthenticationState, TokenType, useAPI } from '../providers/APIProvider'
import { catchErrors } from '../utils/error-component'

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

    return (
        <Switch>
            <Match when={api.authState() === AuthenticationState.LoggedIn && api.tokenType() === TokenType.Admin}>
                <Outlet />
            </Match>
            <Match when={api.authState() === AuthenticationState.Loading}>
                <LoadingPage />
            </Match>
        </Switch>
    )
}

function UnauthorizedRedirect() {
    useAdminLogoutRedirect()
    return null
}

const useAdminLogoutRedirect = () => useLogoutRedirect('/manage/login', TokenType.Admin)
