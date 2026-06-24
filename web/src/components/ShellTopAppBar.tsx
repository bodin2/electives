import ArrowLeftIcon from '@iconify-icons/mdi/arrow-left'
import { useCanGoBack } from '@tanstack/solid-router'
import { type Component, Show } from 'solid-js'
import { Dynamic } from 'solid-js/web'
import { useI18n } from '~/providers/I18nProvider'
import { useMergedPageOptions } from '~/providers/PageOptionsProvider'
import { useShellChrome } from '~/providers/ShellChromeProvider'
import { Button } from './Button'
import SchoolLogo from './images/SchoolLogo'
import { HStack } from './Stack'
import TopAppBar from './TopAppBar'

interface ShellTopAppBarProps {
    chromeLeading?: Component
    chromeTrailing?: Component
}

export default function ShellTopAppBar(props: ShellTopAppBarProps) {
    const chrome = useShellChrome()
    const opts = useMergedPageOptions()
    const canGoBack = useCanGoBack()
    const { string } = useI18n()

    const Leading: Component = () => (
        <HStack gap={8} alignVertical="center">
            <Show when={props.chromeLeading}>{C => <Dynamic component={C()} />}</Show>
            <Show when={opts().headerLeading}>{C => <Dynamic component={C()} />}</Show>
            <Show when={(opts().allowBacking ?? true) && canGoBack()}>
                <Button
                    aria-label={string.BACK()}
                    variant="text"
                    icon={ArrowLeftIcon}
                    iconType="only"
                    onClick={() => history.back()}
                />
            </Show>
        </HStack>
    )

    const Headline: Component = () => (
        <HStack alignHorizontal="center" alignVertical="center" gap={16} style={{ 'padding-inline-start': '8px' }}>
            <SchoolLogo style={{ width: '32px', height: '36px' }} />
            <Show when={opts().title}>{T => <Dynamic component={T()} />}</Show>
        </HStack>
    )

    const Trailing: Component = () => (
        <HStack gap={8} alignVertical="center">
            <Show when={opts().headerTrailing}>{C => <Dynamic component={C()} />}</Show>
            <Show when={props.chromeTrailing}>{C => <Dynamic component={C()} />}</Show>
        </HStack>
    )

    return (
        <TopAppBar
            elevated={chrome.topAppBarElevated()}
            variant="small"
            leading={Leading}
            headline={Headline}
            trailing={Trailing}
        />
    )
}
