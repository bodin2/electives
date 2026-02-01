import { createEffect, type JSX, type JSXElement, type Resource, Show, splitProps } from 'solid-js'
import { usePageData } from '../providers/PageProvider'
import LoadingPage from './pages/LoadingPage'
import { VStack } from './Stack'

interface PageProps extends JSX.HTMLAttributes<HTMLElement> {
    name?: JSXElement
    leading?: JSXElement
    style?: JSX.CSSProperties
    resources?: Resource<unknown>[]
    showLoading?: boolean
}

export default function Page(props: PageProps) {
    const pageData = usePageData()
    const [local, others] = splitProps(props, ['name', 'resources', 'showLoading', 'children', 'leading'])

    createEffect(() => {
        // so ErrorPage works without PageProvider
        if (!pageData) return

        pageData.setTitle(local.name ? () => local.name : '')
        pageData.setTrailing(local.leading ? () => local.leading : undefined)
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
