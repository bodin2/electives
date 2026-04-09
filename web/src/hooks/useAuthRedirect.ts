import Logger from '@bodin2/electives-common/Logger'
import { type Register, type ToPathOption, useRouter } from '@tanstack/solid-router'
import { type Accessor, createEffect, on, untrack } from 'solid-js'
import { AuthenticationState, type TokenType, useAPI } from '../providers/APIProvider'
import type { RoutePath } from '../main'

const log = new Logger('hooks/useLoginRedirect')

export function onLogin(callback: (tokenType: TokenType) => void) {
    const api = useAPI()

    createEffect(
        on(api.authState, state => {
            if (state === AuthenticationState.LoggedIn) {
                const type = api.tokenType()
                if (!type) return console.warn('Auth state is LoggedIn but tokenType is null')
                callback(type)
            }
        }),
    )
}

interface UseLoginRedirectOptions {
    search?: Accessor<string | undefined>
    /**
     * Token type required to trigger the redirect.
     */
    tokenType?: TokenType
    /**
     * Path to redirect when the token type doesn't match, but the user is authenticated.
     */
    altPath?: RoutePath
    /**
     * Delay in milliseconds before performing the redirect. Alternate redirects will not use this delay.
     */
    delay?: number
}

export function useLoginRedirect(path: Accessor<RoutePath>, options: UseLoginRedirectOptions = {}) {
    const router = useRouter()
    const navigate = router.navigate

    const { tokenType, altPath, delay = 0, search } = options

    onLogin(loggedInTokenType => {
        if (tokenType === undefined || loggedInTokenType === tokenType) {
            log.info('Logged in, redirecting to', path)

            untrack(() => {
                router.clearCache({ filter: () => true })
                router.invalidate({ sync: true, filter: () => true })
            })

            const s = search ? search() : undefined

            setTimeout(() => {
                const url = `${path()}${s ? `?${decodeURIComponent(s)}` : ''}`
                navigate({ to: url, replace: true })
            }, delay)
        } else if (altPath) {
            log.info('Logged in with different token type, redirecting to', altPath)
            navigate({ to: altPath, replace: true })
        }
    })
}

export function useLogoutRedirect(path: ToPathOption<Register['router']>, tokenType?: TokenType) {
    const api = useAPI()
    const navigate = useRouter().navigate

    createEffect(
        on(api.authState, authState => {
            if (authState === AuthenticationState.LoggedOut || (tokenType && api.tokenType() !== tokenType)) {
                navigate({
                    to: path,
                    replace: true,
                })
            }
        }),
    )
}
