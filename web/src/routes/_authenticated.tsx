import { createFileRoute, type ErrorRouteComponent, Outlet } from '@tanstack/solid-router'
import { Match, Switch } from 'solid-js'
import { UnauthorizedError } from '../api'
import { PageTopAppBar } from '../components/PageTopAppBar'
import LoadingPage from '../components/pages/LoadingPage'
import { useLogoutRedirect } from '../hooks/useAuthRedirect'
import { AuthenticationState, TokenType, useAPI } from '../providers/APIProvider'
import { usePageData } from '../providers/PageProvider'
import ScrollDataProvider from '../providers/ScrollDataProvider'
import { catchErrors } from '../utils/error-component'

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
    const pageData = usePageData()

    useUserLogoutRedirect()

    return (
        <ScrollDataProvider>
            <Switch>
                <Match when={api.authState() === AuthenticationState.LoggedIn && api.tokenType() === TokenType.User}>
                    <PageTopAppBar elevated={pageData.topAppBarElevated} />
                    <Outlet />
                </Match>
                <Match when={api.authState() === AuthenticationState.Loading}>
                    <LoadingPage />
                </Match>
            </Switch>
        </ScrollDataProvider>
    )
}

function UnauthorizedRedirect() {
    useUserLogoutRedirect()
    return null
}

const useUserLogoutRedirect = () => useLogoutRedirect('/login', TokenType.User)
