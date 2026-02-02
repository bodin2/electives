import { render } from 'solid-js/web'
import 'solid-devtools'

import { createRouter, defer, RouterProvider } from '@tanstack/solid-router'
import { routeTree } from './routeTree.gen'

import 'm3-solid/styles.css'
import './theme.css'
import './styles.css'

import { MetaProvider } from '@solidjs/meta'
import ErrorPage from './components/pages/ErrorPage'
import LoadingPage from './components/pages/LoadingPage'
import NotFoundPage from './components/pages/NotFoundPage'
import { createClient, initAuth } from './providers/APIProvider'
import I18nProvider from './providers/I18nProvider'
import type { RouterContext } from './routes/__root'

const client = createClient()
const authReady = initAuth(client)

const router = createRouter({
    routeTree,
    defaultPreload: 'intent',
    defaultStaleTime: 5000,
    defaultPendingMs: 250,
    defaultPendingMinMs: 250,
    defaultPendingComponent: LoadingPage,
    defaultErrorComponent: ErrorPage,
    defaultNotFoundComponent: NotFoundPage,
    scrollRestoration: true,
    context: { client, authState: defer(authReady) } satisfies RouterContext,
})

declare module '@tanstack/solid-router' {
    interface Register {
        router: typeof router
    }
}

// biome-ignore lint/style/noNonNullAssertion: Known to exist
const rootElement = document.getElementById('app')!

if (!rootElement.innerHTML) {
    render(
        () => (
            <MetaProvider>
                <I18nProvider>
                    <RouterProvider router={router} />
                </I18nProvider>
            </MetaProvider>
        ),
        rootElement,
    )
}
