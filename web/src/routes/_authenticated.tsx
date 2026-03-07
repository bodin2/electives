import {
    createFileRoute,
    type ErrorRouteComponent,
    Outlet,
    useLocation,
    useNavigate,
    useRouter,
} from '@tanstack/solid-router'
import { createEffect, Match, on, Switch } from 'solid-js'
import { UnauthorizedError } from '../api'
import SchoolLogo from '../components/images/SchoolLogo'
import ErrorPage from '../components/pages/ErrorPage'
import LoadingPage from '../components/pages/LoadingPage'
import { HStack } from '../components/Stack'
import TopAppBar from '../components/TopAppBar'
import { AuthenticationState, useAPI } from '../providers/APIProvider'
import { useI18n } from '../providers/I18nProvider'
import { usePageData } from '../providers/PageProvider'

export const AUTHENTICATED_ROUTE_DEFAULTS = {
    errorComponent: props => {
        if (props.error instanceof UnauthorizedError) return <UnauthorizedRedirect />
        throw props.error
    },
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

    useLogoutRedirect()

    return (
        <Switch>
            <Match when={api.authState() === AuthenticationState.LoggedIn}>
                <TopAppBar
                    variant="small"
                    headline={() => (
                        <HStack
                            alignHorizontal="center"
                            alignVertical="center"
                            gap={16}
                            style={{
                                'padding-inline-start': '8px',
                            }}
                        >
                            <SchoolLogo style={{ width: '32px', height: '36px' }} />
                            {typeof pageData.title === 'function' ? pageData.title() : pageData.title}
                        </HStack>
                    )}
                    trailing={pageData.trailing ? () => pageData.trailing?.() : undefined}
                />
                <Outlet />
            </Match>
            <Match when={api.authState() === AuthenticationState.Loading}>
                <LoadingPage />
            </Match>
            <Match when={api.authState() === AuthenticationState.NetworkError}>
                <NetworkErrorPage />
            </Match>
        </Switch>
    )
}

function NetworkErrorPage() {
    const api = useAPI()
    const router = useRouter()
    const { string } = useI18n()

    return (
        <ErrorPage
            error={navigator.onLine ? string.ERROR_API_UNREACHABLE() : string.ERROR_OFFLINE()}
            reset={async () => {
                await api.client.login()
                await router.invalidate({
                    sync: true,
                })
            }}
        />
    )
}

function UnauthorizedRedirect() {
    useLogoutRedirect()
    return null
}

function useLogoutRedirect() {
    const api = useAPI()
    const navigate = useNavigate()
    const location = useLocation()

    createEffect(
        on(api.authState, authState => {
            if (authState === AuthenticationState.LoggedOut) {
                navigate({
                    to: '/login',
                    replace: true,
                    search: {
                        to: location().pathname,
                        search: location().searchStr || undefined,
                    },
                })
            }
        }),
    )
}
