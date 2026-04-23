import HomeIcon from '@iconify-icons/mdi/home'
import { useRouter } from '@tanstack/solid-router'
import { createEffect, createSignal, on, Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import NotFoundIllustration from '../images/NotFoundIllustration'
import LinkButton from '../LinkButton'
import Page from '../Page'
import { VStack } from '../Stack'

export default function NotFoundPage() {
    return (
        <Page>
            <NotFoundPageContent illustration />
        </Page>
    )
}

export function NotFoundPageContent(props: { illustration?: boolean }) {
    const { string } = useI18n()
    const router = useRouter()
    const [stringIndex, setStringIndex] = createSignal(0)

    const strings = [
        string.NOT_FOUND(),
        string.NOT_FOUND_LIKE_ACTUALLY(),
        string.NOT_FOUND_LIKE_REALLY_ACTUALLY(),
        string.NOT_FOUND_DOING_IT_FOR_YOU(),
    ]

    createEffect(
        on(stringIndex, i => {
            if (i >= strings.length - 1) {
                console.warn('you dont know how to follow directions')
                setTimeout(() => {
                    router.navigate({ to: '/' })
                }, 1500)
            }
        }),
    )

    return (
        <VStack gap={16} alignHorizontal="center" alignVertical="center" style={{ height: '100%' }}>
            <VStack alignHorizontal="center" gap={32}>
                <Show when={props.illustration}>
                    <NotFoundIllustration style={{ width: '192px', height: '192px' }} />
                </Show>
                {/** biome-ignore lint/a11y/useKeyWithClickEvents: No */}
                <h1 class="m3-headline-small" tabIndex="-1" onClick={() => setStringIndex(i => i + 1)}>
                    {strings[stringIndex()]}
                </h1>
            </VStack>
            <LinkButton to="/" variant="filled" icon={HomeIcon}>
                {string.BACK_HOME}
            </LinkButton>
        </VStack>
    )
}
