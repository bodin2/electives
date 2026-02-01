/** biome-ignore-all lint/a11y/useHeadingContent: Heading only for cosmetic purposes, couldn't apply it on the line for some reason */

import { type Component, createMemo, createRenderEffect, createSignal, type JSX, on, onCleanup, Show } from 'solid-js'
import { useScrollData } from '../providers/ScrollDataProvider'
import { HStack } from './Stack'
import styles from './TopAppBar.module.css'

const CompressiblePartsConstants = {
    large: {
        PaddingTop: 40,
        HeadlineHeight: 36,
        HalfHeadlineHeight: 18,
        PaddingBottom: 28,
        Height: 104,
    },
    medium: {
        PaddingTop: 0,
        HeadlineHeight: 32,
        HalfHeadlineHeight: 16,
        PaddingBottom: 24,
        Height: 56,
    },
} as const

export default function TopAppBar(props: TopAppBarProps) {
    const sd = useScrollData()
    const isCompressible = createMemo(() => props.variant === 'medium' || props.variant === 'large')
    const [headlineOpacity, setHeadlineOpacity] = createSignal(isCompressible() ? 0 : 1)
    const [compressibleHeadlineOpacity, setCompressibleHeadlineOpacity] = createSignal(isCompressible() ? 1 : 0)
    const getCompressiblePartConstants = createMemo(
        () => CompressiblePartsConstants[props.variant as 'medium' | 'large'],
    )

    // Scroll handling
    createRenderEffect(
        on([isCompressible, getCompressiblePartConstants] as const, ([isCompressible, CompressiblePartConstants]) => {
            if (!isCompressible) return

            const scrollEndListener = () => {
                const lastKnownScrollY = sd.scrollY

                // Preventing stuck scrolls
                setTimeout(() => {
                    // If the scroll position has changed since the scrollend event, do nothing
                    if (Math.abs(lastKnownScrollY - sd.scrollY) > 4) return

                    if (sd.scrollY < CompressiblePartConstants.PaddingTop)
                        window.scrollTo({ top: 0, behavior: 'smooth' })
                    else if (sd.scrollY < CompressiblePartConstants.Height)
                        window.scrollTo({ top: CompressiblePartConstants.Height, behavior: 'smooth' })
                }, 100)
            }

            window.addEventListener('scrollend', scrollEndListener)
            onCleanup(() => window.removeEventListener('scrollend', scrollEndListener))
        }),
    )

    // Headline opacity handling
    createRenderEffect(
        on(
            [isCompressible, getCompressiblePartConstants, () => sd.scrollY] as const,
            ([isCompressible, CompressiblePartConstants, scrollY]) => {
                if (!isCompressible) return

                if (scrollY < CompressiblePartConstants.PaddingTop) {
                    setHeadlineOpacity(0)
                    setCompressibleHeadlineOpacity(1)
                } else {
                    setHeadlineOpacity(
                        Math.min(
                            (scrollY -
                                CompressiblePartConstants.PaddingTop -
                                CompressiblePartConstants.HalfHeadlineHeight) /
                                CompressiblePartConstants.HeadlineHeight,
                            1,
                        ),
                    )

                    setCompressibleHeadlineOpacity(
                        Math.max(
                            1 -
                                (scrollY - CompressiblePartConstants.PaddingTop) /
                                    CompressiblePartConstants.HalfHeadlineHeight,
                            0,
                        ),
                    )
                }
            },
        ),
    )

    return (
        <>
            <TopAppBarCompactPart
                justify={props.variant === 'small-centered' ? 'space-between' : undefined}
                scrolledVertical={sd.scrolledVertical}
                leading={props.leading}
                headline={props.headline}
                headlineOpacity={headlineOpacity()}
                trailing={props.trailing}
            />
            {isCompressible() && (
                <TopAppBarCompressiblePart
                    variant={props.variant as never}
                    headline={props.headline}
                    headlineOpacity={compressibleHeadlineOpacity()}
                />
            )}
        </>
    )
}

interface TopAppBarProps {
    headline: Component
    leading?: Component
    trailing?: Component
    variant?: 'small' | 'medium' | 'large' | 'small-centered'
}

function TopAppBarCompactPart(props: TopAppBarCompactPartProps) {
    return (
        <HStack
            as="header"
            gap={4}
            class={styles.container}
            alignHorizontal={props.justify ?? 'start'}
            classList={{
                [styles.scrolled]: props.scrolledVertical,
            }}
        >
            <Show when={props.leading}>
                <div>{props.leading!({})}</div>
            </Show>
            <HStack class="m3-title-large" as="h1" grow style={{ opacity: props.headlineOpacity }}>
                <Show when={props.headline}>{props.headline!({})}</Show>
            </HStack>
            <Show when={props.trailing}>{props.trailing!({})}</Show>
        </HStack>
    )
}

interface TopAppBarCompactPartProps {
    headline: Component
    headlineOpacity?: number
    leading?: Component
    trailing?: Component
    justify?: 'start' | 'space-between'
    scrolledVertical?: boolean
}

function TopAppBarCompressiblePart(props: TopAppBarCompressiblePartProps) {
    return (
        <h1
            aria-hidden="true"
            class={`${styles.compressible} ${props.variant === 'medium' ? 'headline-small' : 'headline-medium'}`}
            classList={{
                [styles.large]: props.variant === 'large',
            }}
            style={{
                opacity: props.headlineOpacity,
            }}
        >
            {props.headline!({})}
        </h1>
    )
}

interface TopAppBarCompressiblePartProps {
    variant: 'medium' | 'large'
    headline: Component
    headlineOpacity?: number
}

export function TopAppBarTrailingIcons(props: TopAppBarTrailingIconsProps) {
    return <HStack gap={0}>{props.children}</HStack>
}

interface TopAppBarTrailingIconsProps {
    children: NonArrayElement | [NonArrayElement, NonArrayElement] | [NonArrayElement, NonArrayElement, NonArrayElement]
}

type NonArrayElement = Exclude<JSX.Element, Array<unknown>>
