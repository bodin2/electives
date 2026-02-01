import Logger from '@bodin2/electives-common/Logger'
import { createFileRoute, useNavigate, useSearch } from '@tanstack/solid-router'
import { Dialog, TextField, type TextFieldProps } from 'm3-solid'
import { createEffect, createSignal } from 'solid-js'
import { UnauthorizedError } from '../api'
import { Button } from '../components/Button'
import SchoolLogo from '../components/images/SchoolLogo'
import Page from '../components/Page'
import { VStack } from '../components/Stack'
import Version from '../components/Version'
import { AuthenticationState, useAPI } from '../providers/APIProvider'
import { useI18n } from '../providers/I18nProvider'

const log = new Logger('routes/login')

type LoginSearch = {
    to?: string
    search?: string
}

export const Route = createFileRoute('/login')({
    component: Login,
    validateSearch: (search: Record<string, unknown>): LoginSearch => ({
        to: typeof search.to === 'string' ? search.to : undefined,
        search: typeof search.search === 'string' ? search.search : undefined,
    }),
})

function Login() {
    const { string } = useI18n()
    const api = useAPI()
    const navigate = useNavigate()
    const search = useSearch({ from: '/login' }) as () => LoginSearch

    const [loading, setLoading] = createSignal(false)

    const [inputExtraProps, setInputExtraProps] = createSignal<Partial<TextFieldProps>>({})

    createEffect(() => {
        if (api.$authState() === AuthenticationState.LoggedIn) {
            log.info('Logged in, redirecting to home')
            setTimeout(() => {
                const s = search()
                const url = s.to ? `${s.to}${s.search ? `?${decodeURIComponent(s.search)}` : ''}` : '/'
                navigate({ to: url, replace: true })
            }, 350)
        }
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
                        <VStack gap={4} alignHorizontal="center" style={{ 'margin-bottom': '16px' }}>
                            {string.ELECTIVES_SYSTEM()}
                            <p class="m3-body-medium">{string.LOGIN_HINT()}</p>
                        </VStack>
                    </VStack>
                }
                actions={
                    <Button style={{ flex: 1 }} loading={loading()} onClick={() => form.requestSubmit()}>
                        {string.LOGIN()}
                    </Button>
                }
                open={api.$authState() !== AuthenticationState.LoggedIn}
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
                                studentId.value.match(/^\d+$/) ? '' : string.ERROR_STUDENT_ID_NUMERIC(),
                            )
                            studentId.reportValidity()

                            password.setCustomValidity(password.value ? '' : string.ERROR_PASSWORD_REQUIRED())
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
                            <TextField
                                errorIcon
                                label={string.STUDENT_ID()}
                                autocomplete="username"
                                {...inputExtraProps()}
                            />
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
