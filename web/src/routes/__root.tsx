import { Title } from '@solidjs/meta'
import { createRootRouteWithContext, Outlet, useRouteContext } from '@tanstack/solid-router'
import { TanStackRouterDevtools } from '@tanstack/solid-router-devtools'
import { Show } from 'solid-js'
import APIProvider, { type AuthenticationState } from '../providers/APIProvider'
import { EnrollmentCountsProvider } from '../providers/EnrollmentCountsProvider'
import { useI18n } from '../providers/I18nProvider'
import PageDataProvider from '../providers/PageProvider'
import ScrollDataProvider from '../providers/ScrollDataProvider'
import type { Client } from '../api'

export interface RouterContext {
    client: Client
    authState: Promise<AuthenticationState>
}

export const Route = createRootRouteWithContext<RouterContext>()({
    component: RootComponent,
})

function RootComponent() {
    const context = useRouteContext({ from: '__root__' })

    return (
        <PageDataProvider>
            <ScrollDataProvider>
                <APIProvider client={context().client}>
                    <EnrollmentCountsProvider client={context().client}>
                        <I18nReadyOutlet />
                        <TanStackRouterDevtools position="bottom-left" />
                    </EnrollmentCountsProvider>
                </APIProvider>
            </ScrollDataProvider>
        </PageDataProvider>
    )
}

function I18nReadyOutlet() {
    const i18n = useI18n()

    return (
        <Show when={i18n.ready}>
            <Title>{i18n.string.ELECTIVES_SYSTEM()}</Title>
            <Outlet />
        </Show>
    )
}
