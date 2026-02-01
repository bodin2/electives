import HomeIcon from '@iconify-icons/mdi/home'
import { Show } from 'solid-js'
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

    return (
        <VStack gap={16} alignHorizontal="center" alignVertical="center" style={{ height: '100%' }}>
            <VStack alignHorizontal="center" gap={32}>
                <Show when={props.illustration}>
                    <NotFoundIllustration style={{ width: '192px', height: '192px' }} />
                </Show>
                <h1 class="m3-headline-small">{string.NOT_FOUND()}</h1>
            </VStack>
            <LinkButton to="/" variant="filled" icon={HomeIcon}>
                {string.BACK_HOME}
            </LinkButton>
        </VStack>
    )
}
