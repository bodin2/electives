import Logger from '@bodin2/electives-common/Logger'
import { LoadingIndicator } from 'm3-solid/src'
import { onCleanup, onMount, type ParentComponent, Show, Suspense } from 'solid-js'
import { DEV } from '~/constants'
import { useI18n } from '~/providers/I18nProvider'
import { VStack } from '../Stack'

const logger = new Logger('LoadingPage')

// TODO: Make this a preference
const SUSPENSE_BOUNDARY_DEBUG = true && DEV

export default function LoadingPage(props: { debugName?: string }) {
    const { string } = useI18n()

    // Only log when the suspense boundary is actually shown, not just when it's mounted
    if (SUSPENSE_BOUNDARY_DEBUG)
        onMount(() => {
            const debugName = props.debugName ?? 'UNKNOWN'

            logger.trace(`Suspense boundary resolved: ${debugName}`)

            const id = requestAnimationFrame(() => logger.trace(`Suspense boundary shown: ${debugName}`))
            onCleanup(() => cancelAnimationFrame(id))
        })

    return (
        <VStack
            alignHorizontal="center"
            alignVertical="center"
            grow
            style={SUSPENSE_BOUNDARY_DEBUG ? { background: 'red' } : undefined}
        >
            <div>
                <LoadingIndicator container />
            </div>
            <p class="m3-body-large" style={{ color: 'var(--m3c-on-surface)' }}>
                <Show when={!SUSPENSE_BOUNDARY_DEBUG || !props.debugName} fallback={props.debugName}>
                    {string.LOADING()}
                </Show>
            </p>
        </VStack>
    )
}

export const SuspenseLoadingPage: ParentComponent<{ debugName?: string }> = props => {
    return <Suspense fallback={<LoadingPage debugName={props.debugName} />}>{props.children}</Suspense>
}
