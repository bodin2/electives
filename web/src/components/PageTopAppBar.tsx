import ArrowLeftIcon from '@iconify-icons/mdi/arrow-left'
import { useCanGoBack } from '@tanstack/solid-router'
import { Show } from 'solid-js'
import { useI18n } from '../providers/I18nProvider'
import { usePageData } from '../providers/PageProvider'
import { Button } from './Button'
import SchoolLogo from './images/SchoolLogo'
import { HStack } from './Stack'
import TopAppBar from './TopAppBar'

export function PageTopAppBar(props: { elevated?: boolean }) {
    const pageData = usePageData()
    const canGoBack = useCanGoBack()
    const { string } = useI18n()

    return (
        <TopAppBar
            elevated={props.elevated}
            variant="small"
            leading={() => (
                <HStack gap={8}>
                    <Show when={pageData.leading}>
                        {typeof pageData.leading === 'function' ? pageData.leading() : pageData.leading}
                    </Show>
                    <Show when={pageData.allowBacking && canGoBack()}>
                        <Button
                            aria-label={string.BACK()}
                            variant="text"
                            icon={ArrowLeftIcon}
                            iconType="only"
                            onClick={() => history.back()}
                        />
                    </Show>
                </HStack>
            )}
            headline={() => (
                <HStack
                    alignHorizontal="center"
                    alignVertical="center"
                    gap={16}
                    style={{
                        'padding-inline-start': '8px',
                    }}
                >
                    <SchoolLogo style={{ width: '32px', height: '36px' }} />
                    {typeof pageData.title === 'function' ? pageData.title() : pageData.title}
                </HStack>
            )}
            trailing={pageData.trailing ? () => pageData.trailing?.() : undefined}
        />
    )
}
