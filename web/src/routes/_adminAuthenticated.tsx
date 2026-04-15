import { createFileRoute, type ErrorRouteComponent, Outlet } from '@tanstack/solid-router'
import { Match, Switch } from 'solid-js'
import { UnauthorizedError } from '../api'
import { PageTopAppBar } from '../components/PageTopAppBar'
import ErrorPage from '../components/pages/ErrorPage'
import LoadingPage from '../components/pages/LoadingPage'
import { useLogoutRedirect } from '../hooks/useAuthRedirect'
import { AuthenticationState, TokenType, useAPI } from '../providers/APIProvider'
import { useI18n } from '../providers/I18nProvider'
import { usePageData } from '../providers/PageProvider'

export const ADMIN_AUTHENTICATED_ROUTE_DEFAULTS = {
    errorComponent: props => {
        if (props.error instanceof UnauthorizedError) return <UnauthorizedRedirect />
        throw props.error
    },
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
    const { string } = useI18n()
    const pageData = usePageData()

    useAdminLogoutRedirect()

    return (
        <Switch>
            <Match when={api.authState() === AuthenticationState.LoggedIn && api.tokenType() === TokenType.Admin}>
                <PageTopAppBar elevated={pageData.topAppBarElevated} />
                <Outlet />
            </Match>
            <Match when={api.authState() === AuthenticationState.Loading}>
                <LoadingPage />
            </Match>
            <Match when={api.authState() === AuthenticationState.NetworkError}>
                <ErrorPage
                    error={navigator.onLine ? string.ERROR_API_UNREACHABLE() : string.ERROR_OFFLINE()}
                    reset={() => api.resumeSession()}
                />
            </Match>
        </Switch>
    )
}

function UnauthorizedRedirect() {
    useAdminLogoutRedirect()
    return null
}

const useAdminLogoutRedirect = () => useLogoutRedirect('/manage/login', TokenType.Admin)
