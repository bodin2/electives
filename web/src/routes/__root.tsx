import { Title } from '@solidjs/meta'
import { createRootRouteWithContext, Outlet, useRouteContext } from '@tanstack/solid-router'
import { TanStackRouterDevtools } from '@tanstack/solid-router-devtools'
import { Match, Show, Switch } from 'solid-js'
import { NetworkErrorPage } from '~/components/pages/ErrorPage'
import { BaseSubjectDisplayContext, SubjectDisplayContextProvider } from '~/components/subjects/SubjectDisplayContext'
import { BaseUserDisplayContext, UserDisplayContextProvider } from '~/components/users/UserDisplayContext'
import APIProvider, { AuthenticationState, useAPI } from '~/providers/APIProvider'
import { EnrollmentCountsProvider } from '~/providers/EnrollmentCountsProvider'
import { useI18n } from '~/providers/I18nProvider'
import PageDataProvider from '~/providers/PageProvider'
import type { QueryClient } from '@tanstack/solid-query'
import type { Client } from '~/api'

export interface RouterContext {
    client: Client<unknown>
    authState: Promise<AuthenticationState>
    queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
    component: RootComponent,
})

function RootComponent() {
    const context = useRouteContext({ from: '__root__' })

    return (
        <PageDataProvider>
            <APIProvider client={context().client}>
                <EnrollmentCountsProvider client={context().client}>
                    <SubjectDisplayContextProvider value={BaseSubjectDisplayContext}>
                        <UserDisplayContextProvider value={BaseUserDisplayContext}>
                            <ReadyOutlet />
                            <TanStackRouterDevtools position="bottom-left" />
                        </UserDisplayContextProvider>
                    </SubjectDisplayContextProvider>
                </EnrollmentCountsProvider>
            </APIProvider>
        </PageDataProvider>
    )
}

function ReadyOutlet() {
    const i18n = useI18n()
    const api = useAPI()

    return (
        <Show when={i18n.ready}>
            <Switch>
                <Match when={api.authState() === AuthenticationState.NetworkError}>
                    <NetworkErrorPage />
                </Match>
                <Match when={true}>
                    <Title>{i18n.string.ELECTIVES_SYSTEM()}</Title>
                    <Outlet />
                </Match>
            </Switch>
        </Show>
    )
}
