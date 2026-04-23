import Logger from '@bodin2/electives-common/Logger'
import { createFileRoute, useSearch } from '@tanstack/solid-router'
import { Dialog, TextField, type TextFieldProps } from 'm3-solid'
import { createSignal, Show } from 'solid-js'
import { UnauthorizedError } from '../api'
import { Button } from '../components/Button'
import SchoolLogo from '../components/images/SchoolLogo'
import LinkButton from '../components/LinkButton'
import Page from '../components/Page'
import { VStack } from '../components/Stack'
import Version from '../components/Version'
import { useLoginRedirect } from '../hooks/useAuthRedirect'
import { AuthenticationState, TokenType, useAPI } from '../providers/APIProvider'
import { useI18n } from '../providers/I18nProvider'
import type { RoutePath } from '../main'

const log = new Logger('routes/login')

type LoginSearch = {
    to?: string
    search?: string
    from_admin?: boolean
}

export const Route = createFileRoute('/login')({
    component: Login,
    validateSearch: (search: Record<string, unknown>): LoginSearch => ({
        to: typeof search.to === 'string' ? search.to : undefined,
        search: typeof search.search === 'string' ? search.search : undefined,
        from_admin: typeof search.from_admin === 'boolean' ? search.from_admin : undefined,
    }),
})

function Login() {
    const { string } = useI18n()
    const api = useAPI()
    const search = useSearch({ from: '/login' }) as () => LoginSearch

    const [loading, setLoading] = createSignal(false)

    const [inputExtraProps, setInputExtraProps] = createSignal<Partial<TextFieldProps>>({})

    useLoginRedirect(() => (search().to || '/') as RoutePath, {
        tokenType: TokenType.User,
        altPath: '/manage',
        search: () => search().search,
        delay: 350,
    })

    let form!: HTMLFormElement

    return (
        <Page>
            <Dialog
                closedBy="none"
                aria-label={string.LOGIN}
                centerHeadline
                headline={
                    <VStack gap={16}>
                        <SchoolLogo style={{ width: '48px', height: '55px', 'align-self': 'center' }} />
                        <VStack gap={8} alignHorizontal="center" style={{ 'margin-bottom': '16px' }}>
                            {string.ELECTIVES_SYSTEM()}
                            <p class="m3-body-medium text-surface-variant">{string.LOGIN_HINT()}</p>
                        </VStack>
                    </VStack>
                }
                actions={
                    <VStack gap={8} style={{ flex: 1 }}>
                        <Show when={search().from_admin}>
                            <LinkButton variant="tonal" to="/manage/login">
                                {string.SWITCH_TO_ADMIN_LOGIN()}
                            </LinkButton>
                        </Show>
                        <Button loading={loading()} onClick={() => form.requestSubmit()}>
                            {string.LOGIN()}
                        </Button>
                    </VStack>
                }
                open={api.authState() === AuthenticationState.LoggedOut}
            >
                <VStack
                    gap={24}
                    style={{
                        '--m3-outlined-text-field-label-background-color': 'var(--m3c-surface-container-high)',
                    }}
                >
                    <form
                        onKeyDown={e => {
                            if (e.key === 'Enter') {
                                form.requestSubmit()
                            }
                        }}
                        ref={form}
                        onSubmit={e => {
                            e.preventDefault()

                            const [studentId, password] = form.elements as Iterable<HTMLInputElement>
                            studentId.setCustomValidity(
                                studentId.value.match(/^\d+$/)
                                    ? ''
                                    : string.ERROR_NUMERIC_VALUE({ field: string.ID() }),
                            )
                            studentId.reportValidity()

                            password.setCustomValidity(
                                password.value ? '' : string.ERROR_REQUIRED_FIELD({ field: string.PASSWORD() }),
                            )
                            password.reportValidity()

                            for (const input of [studentId, password]) if (!input.validity.valid) return

                            const promise = api.login(Number(studentId.value), password.value)

                            setLoading(true)

                            promise
                                .catch(err => {
                                    log.error('Login failed', err)

                                    if (err instanceof UnauthorizedError) {
                                        setInputExtraProps({
                                            error: true,
                                            supportingText: string.ERROR_INVALID_CREDENTIALS(),
                                        })

                                        return
                                    }

                                    setInputExtraProps({
                                        error: true,
                                        supportingText: err.message,
                                    })
                                })
                                .finally(() => {
                                    setLoading(false)
                                })
                        }}
                    >
                        <VStack gap={16}>
                            <TextField errorIcon label={string.ID()} autocomplete="username" {...inputExtraProps()} />
                            <TextField
                                errorIcon
                                label={string.PASSWORD()}
                                type="password"
                                autocomplete="current-password"
                                {...inputExtraProps()}
                            />
                        </VStack>
                    </form>
                </VStack>
            </Dialog>
            <Version />
        </Page>
    )
}
