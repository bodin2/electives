import { onMount, Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import ErrorIllustration from '../images/ErrorIllustration'
import Page from '../Page'
import { HStack, VStack } from '../Stack'
import Version from '../Version'

export default function ErrorPage(props: { error: string | Error; reset: () => void | Promise<void> }) {
    const { string } = useI18n()

    onMount(() => {
        console.error('ErrorPage caught an error:', props.error)
    })

    return (
        <Page>
            <VStack gap={24} alignHorizontal="center" alignVertical="center" style={{ height: '100%' }}>
                <VStack alignHorizontal="center" gap={32}>
                    <ErrorIllustration style={{ width: '192px', height: '192px' }} />
                    <VStack alignHorizontal="center" gap={8}>
                        <h1 class="m3-headline-small">{string.ERROR()}</h1>
                        <Show
                            when={props.error instanceof Error}
                            fallback={
                                <p class="m3-body-medium" style={{ color: 'var(--m3c-error)' }}>
                                    {props.error as string}
                                </p>
                            }
                        >
                            <pre
                                class="m3-body-medium"
                                style={{ 'font-family': 'monospace', color: 'var(--m3c-error)' }}
                            >
                                {String(props.error)}
                            </pre>
                        </Show>
                    </VStack>
                </VStack>
                <HStack>
                    {/* <LinkButton to="https://github.com/bodin2/electives/issues/new" variant="tonal">
                            Report this issue
                        </LinkButton> */}
                    <Button onClick={() => window.location.reload()} variant="tonal">
                        {string.RELOAD()}
                    </Button>
                    <Button onClick={props.reset} variant="filled">
                        {string.RETRY()}
                    </Button>
                </HStack>
            </VStack>
            <Version />
        </Page>
    )
}
