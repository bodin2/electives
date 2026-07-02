import Logger from '@bodin2/electives-common/Logger'
import { type Register, type ToPathOption, useRouter } from '@tanstack/solid-router'
import { type Accessor, createEffect, on, untrack } from 'solid-js'
import { LoggedInState, LoggedOutState, useAPI } from '~/providers/APIProvider'
import type { RoutePath } from '~/main'

const log = new Logger('hooks/useLoginRedirect')

export function onLogin(callback: () => void) {
    const api = useAPI()

    createEffect(
        on(api.authState, state => {
            if (state instanceof LoggedInState) callback()
        }),
    )
}

interface UseLoginRedirectOptions {
    search?: Accessor<string | undefined>
    /**
     * Delay in milliseconds before performing the redirect.
     */
    delay?: number
}

export function useLoginRedirect(path: Accessor<RoutePath>, options: UseLoginRedirectOptions = {}) {
    const router = useRouter()
    const navigate = router.navigate

    const { delay = 0, search } = options

    onLogin(() => {
        log.info('Logged in, redirecting to', path)

        untrack(() => {
            router.clearCache()
            router.invalidate({ sync: true })
        })

        const s = search ? search() : undefined

        setTimeout(() => {
            const url = `${path()}${s ? `?${decodeURIComponent(s)}` : ''}`
            navigate({ to: url, replace: true })
        }, delay)
    })
}

export function useLogoutRedirect(path: ToPathOption<Register['router']>) {
    const api = useAPI()
    const navigate = useRouter().navigate

    createEffect(
        on(api.authState, authState => {
            if (authState instanceof LoggedOutState) {
                navigate({
                    to: path,
                    replace: true,
                })
            }
        }),
    )
}
