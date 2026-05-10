import Logger from '@bodin2/electives-common/Logger'
import { createFileRoute } from '@tanstack/solid-router'
import { Dialog } from 'm3-solid'
import { createSignal } from 'solid-js'
import { importPrivateKey } from '../../api/ssh'
import { Button } from '../../components/Button'
import SchoolLogo from '../../components/images/SchoolLogo'
import LinkButton from '../../components/LinkButton'
import Page from '../../components/Page'
import { VStack } from '../../components/Stack'
import Version from '../../components/Version'
import { useLoginRedirect } from '../../hooks/useAuthRedirect'
import { AuthenticationState, TokenType, useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'

const log = new Logger('routes/manage/login')

export const Route = createFileRoute('/manage/login')({
    component: AdminLogin,
})

function AdminLogin() {
    const { string } = useI18n()
    const api = useAPI()

    const [loading, setLoading] = createSignal(false)
    const [error, setError] = createSignal<string>()

    useLoginRedirect(() => '/manage', { tokenType: TokenType.Admin, altPath: '/login' })

    let fileInput!: HTMLInputElement

    const handleImport = () => {
        fileInput.click()
    }

    const handleFileChange = async () => {
        const file = fileInput.files?.[0]
        if (!file) return

        const pem = await file.text()

        setLoading(true)
        setError(undefined)

        try {
            const key = await importPrivateKey(pem)
            await api.adminLogin(key)
        } catch (err) {
            log.error('Admin login failed', err)
            setError(err instanceof Error ? err.message : string.ERROR_INVALID_PRIVATE_KEY())
        } finally {
            setLoading(false)
        }
    }

    return (
        <Page>
            <Dialog
                closedBy="none"
                aria-label={string.ADMIN_LOGIN}
                centerHeadline
                headline={
                    <VStack gap={16}>
                        <SchoolLogo
                            style={{ width: '48px', height: '55px', 'align-self': 'center' }}
                            imageProps={{ fetchpriority: 'high' }}
                        />
                        <VStack gap={8} alignHorizontal="center">
                            {string.ADMIN_LOGIN()}
                            <p class="m3-body-medium text-surface-variant">{string.ADMIN_LOGIN_HINT()}</p>
                        </VStack>
                    </VStack>
                }
                actions={
                    <VStack gap={8} style={{ flex: 1 }}>
                        <LinkButton variant="tonal" to="/login" search={{ from_admin: true }}>
                            {string.SWITCH_TO_USER_LOGIN()}
                        </LinkButton>
                        <Button loading={loading()} onClick={handleImport}>
                            {string.IMPORT_PRIVATE_KEY()}
                        </Button>
                    </VStack>
                }
                open={api.authState() === AuthenticationState.LoggedOut}
            >
                {error() && (
                    <p class="m3-body-medium" style={{ color: 'var(--m3c-error)' }}>
                        {error()}
                    </p>
                )}
                <input ref={fileInput} type="file" accept=".pem" hidden onChange={handleFileChange} />
            </Dialog>
            <Version />
        </Page>
    )
}
