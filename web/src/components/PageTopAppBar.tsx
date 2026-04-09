import ArrowLeftIcon from '@iconify-icons/mdi/arrow-left'
import { useCanGoBack } from '@tanstack/solid-router'
import { Show } from 'solid-js'
import { useI18n } from '../providers/I18nProvider'
import { usePageData } from '../providers/PageProvider'
import { Button } from './Button'
import SchoolLogo from './images/SchoolLogo'
import { HStack } from './Stack'
import TopAppBar from './TopAppBar'

export function PageTopAppBar() {
    const pageData = usePageData()
    const canGoBack = useCanGoBack()
    const { string } = useI18n()

    return (
        <TopAppBar
            variant="small"
            leading={() => (
                <Show when={canGoBack()}>
                    <Button
                        aria-label={string.BACK()}
                        variant="text"
                        icon={ArrowLeftIcon}
                        iconType="only"
                        onClick={() => history.back()}
                    />
                </Show>
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
