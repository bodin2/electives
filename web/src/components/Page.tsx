import { createEffect, type JSX, type JSXElement, onCleanup, onMount, type Resource, Show, splitProps } from 'solid-js'
import { usePageData } from '../providers/PageProvider'
import LoadingPage from './pages/LoadingPage'
import { VStack } from './Stack'

interface PageProps extends JSX.HTMLAttributes<HTMLElement> {
    /**
     * null = inherit
     */
    name?: JSXElement | null
    /**
     * null = inherit
     */
    leading?: JSXElement | null
    /**
     * null = inherit
     */
    trailing?: JSXElement | null
    /**
     * undefined = inherit
     */
    allowBacking?: boolean
    style?: JSX.CSSProperties
    resources?: Resource<unknown>[]
    showLoading?: boolean
}

export default function Page(props: PageProps) {
    const pageData = usePageData()
    const [local, others] = splitProps(props, [
        'name',
        'resources',
        'showLoading',
        'children',
        'leading',
        'trailing',
        'allowBacking',
    ])

    onMount(() => {
        if (!pageData) return

        const prevTitle = pageData.title
        const prevLeading = pageData.leading
        const prevTrailing = pageData.trailing
        const prevAllowBacking = pageData.allowBacking

        onCleanup(() => {
            pageData.setTitle(prevTitle)
            pageData.setLeading(prevLeading)
            pageData.setTrailing(prevTrailing)
            pageData.setAllowBacking(prevAllowBacking)
        })
    })

    createEffect(() => {
        // so ErrorPage works without PageProvider
        if (!pageData) return

        if (local.name !== null) {
            const name = local.name
            pageData.setTitle(name !== undefined ? () => name : '')
        }

        if (local.leading !== null) {
            const leading = local.leading
            pageData.setLeading(leading !== undefined ? () => leading : undefined)
        }

        if (local.trailing !== null) {
            const trailing = local.trailing
            pageData.setTrailing(trailing !== undefined ? () => trailing : undefined)
        }

        if (local.allowBacking !== undefined) pageData.setAllowBacking(local.allowBacking)
    })

    const allReady = () => {
        if (!local.resources) return true

        for (const resource of local.resources) {
            if (resource.loading) return false
        }

        return true
    }

    return (
        <VStack gap={0} as="main" grow {...others}>
            <Show when={allReady()} fallback={local.showLoading ? <LoadingPage /> : null}>
                {local.children}
            </Show>
        </VStack>
    )
}
