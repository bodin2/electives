import { useRouter } from '@tanstack/solid-router'
import { mergeClasses } from 'm3-solid/src'
import { createRenderEffect, Show } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../Button'
import ErrorIllustration from '../images/ErrorIllustration'
import Page from '../Page'
import { HStack, VStack } from '../Stack'
import Version from '../Version'
import styles from './ErrorPage.module.css'

export default function ErrorPage(props: { error: string | Error; reset: () => void | Promise<void> }) {
    const { string } = useI18n()
    const router = useRouter()

    createRenderEffect(() => {
        console.error('ErrorPage caught an error:', props.error)
    })

    return (
        <Page trailing={null} leading={null}>
            <VStack gap={24} alignHorizontal="center" alignVertical="center" grow>
                <VStack alignHorizontal="center" gap={32} style={{ width: '100%', 'padding-inline': '16px' }}>
                    <ErrorIllustration style={{ width: '192px', height: '192px' }} />
                    <VStack alignHorizontal="center" gap={8} style={{ width: '100%' }}>
                        <h1 class="m3-headline-small text-balance text-center">{string.ERROR()}</h1>
                        <Show
                            when={props.error instanceof Error}
                            fallback={
                                <p class={mergeClasses('m3-body-medium', styles.text)}>{props.error as string}</p>
                            }
                        >
                            <pre
                                class={mergeClasses('m3-body-medium', styles.text)}
                                style={{ 'font-family': 'monospace' }}
                            >
                                {String(props.error)}
                            </pre>
                        </Show>
                    </VStack>
                </VStack>
                <HStack alignHorizontal="center" wrap>
                    {/* <LinkButton to="https://github.com/bodin2/electives/issues/new" variant="tonal">
                            Report this issue
                        </LinkButton> */}
                    <Button onClick={() => window.location.reload()} variant="tonal">
                        {string.RELOAD()}
                    </Button>
                    <Button
                        onClick={() => {
                            router.invalidate({ sync: true })
                            props.reset()
                        }}
                        variant="filled"
                    >
                        {string.RETRY()}
                    </Button>
                </HStack>
            </VStack>
            <Version />
        </Page>
    )
}

export function NetworkErrorPage() {
    const api = useAPI()
    const { string } = useI18n()

    return (
        <ErrorPage
            error={navigator.onLine ? string.ERROR_API_UNREACHABLE() : string.ERROR_OFFLINE()}
            reset={() => api.resumeSession()}
        />
    )
}
