import { type Component, type JSX, type JSXElement, splitProps } from 'solid-js'
import { usePageOptions } from '~/providers/PageOptionsProvider'
import { useShellChrome } from '~/providers/ShellChromeProvider'
import { SuspenseLoadingPage } from './pages/LoadingPage'
import { VStack } from './Stack'

interface PageProps extends JSX.HTMLAttributes<HTMLElement> {
    /** Page title. Omit to inherit. */
    name?: JSXElement
    /** Extra leading content for the top app bar (e.g. tabs). Omit to inherit. */
    leading?: JSXElement
    /** Extra trailing content for the top app bar (e.g. page actions). Omit to inherit. */
    trailing?: JSXElement
    /** Whether to show the back button. Omit to inherit. */
    allowBacking?: boolean
    style?: JSX.CSSProperties
    showLoading?: boolean
}

export default function Page(props: PageProps) {
    const chrome = useShellChrome()
    const [local, others] = splitProps(props, [
        'name',
        'leading',
        'trailing',
        'allowBacking',
        'showLoading',
        'children',
    ])

    const TitleComp: Component = () => <>{local.name}</>
    const LeadingComp: Component = () => <>{local.leading}</>
    const TrailingComp: Component = () => <>{local.trailing}</>

    usePageOptions(() => ({
        title: local.name !== undefined ? TitleComp : undefined,
        headerLeading: local.leading !== undefined ? LeadingComp : undefined,
        headerTrailing: local.trailing !== undefined ? TrailingComp : undefined,
        allowBacking: local.allowBacking,
    }))

    return (
        <VStack gap={0} as="main" grow inert={!chrome.focusable()} {...others}>
            <SuspenseLoadingPage debugName="Page">{local.children}</SuspenseLoadingPage>
        </VStack>
    )
}
