import HomeIcon from '@iconify-icons/mdi/home'
import { mergeClasses } from 'm3-solid/src'
import { createSignal, onMount, Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import NotFoundIllustration, { NotFoundEEIllustration } from '../images/NotFoundIllustration'
import LinkButton from '../LinkButton'
import Page from '../Page'
import { HStack, VStack } from '../Stack'
import styles from './NotFoundPage.module.css'

export default function NotFoundPage() {
    return (
        <Page allowBacking leading={null} trailing={null}>
            <NotFoundPageContent illustration />
        </Page>
    )
}

const COUNTER_KEY = 'not_found_count'
const COUNTER_THRESHOLD = 10
const COUNTER_RED_THRESHOLD = 50

export function NotFoundPageContent(props: { illustration?: boolean }) {
    const { string } = useI18n()
    const [count, setCount] = createSignal(0)
    const [dialogOpen, setDialogOpen] = createSignal(false)

    onMount(() => {
        const count = Number.parseInt(localStorage.getItem(COUNTER_KEY) || '0', 10) + 1
        localStorage.setItem(COUNTER_KEY, count.toString())
        setCount(count)
    })

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
            <Show when={props.illustration}>
                <Show when={count() >= COUNTER_THRESHOLD}>
                    <div class={styles.ee} aria-hidden="true">
                        <NotFoundEEIllustration
                            tabindex="-1"
                            class={styles.popup}
                            onClick={() => setDialogOpen(true)}
                        />
                    </div>
                </Show>
                <Dialog
                    closedBy="any"
                    actions={
                        <HStack wrap>
                            <Button
                                variant="tonal-error"
                                onClick={() => {
                                    localStorage.setItem(COUNTER_KEY, '0')
                                    setCount(0)
                                    setDialogOpen(false)
                                }}
                            >
                                {string.NOT_FOUND_EE_RESET()}
                            </Button>
                            <Button onClick={() => setDialogOpen(false)}>{string.CLOSE()}</Button>
                        </HStack>
                    }
                    open={dialogOpen()}
                    onClose={() => setDialogOpen(false)}
                    centerHeadline
                    headline={string.NOT_FOUND_EE_TITLE()}
                >
                    <p>
                        {string.NOT_FOUND_EE_DESCRIPTION({
                            count: (
                                <span class={mergeClasses(count() >= COUNTER_RED_THRESHOLD && 'text-error')}>
                                    {count()}
                                </span>
                            ),
                        })}
                    </p>
                </Dialog>
            </Show>
        </VStack>
    )
}
