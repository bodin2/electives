import { Menu } from 'm3-solid/src'
import { createEffect, createSignal, type JSX, on, onCleanup, Show } from 'solid-js'
import styles from './AnchoredMenu.module.css'

export type AnchoredMenuPlacement = 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end'

type AnchorInput = HTMLElement | undefined | (() => HTMLElement | undefined)

export interface AnchoredMenuProps {
    /** Anchor element the menu is positioned relative to. */
    anchor: AnchorInput
    open: boolean
    onClose: () => void
    /** Preferred placement; flips automatically when overflowing. */
    placement?: AnchoredMenuPlacement
    /** Gap between anchor and menu in pixels. */
    offset?: number
    /** If true, the menu's min-width is set to the anchor's width. */
    matchAnchorWidth?: boolean
    class?: string
    children: JSX.Element
}

interface ResolvedPosition {
    top: number
    left: number
    maxHeight: number
    minWidth: number | null
    transformOrigin: string
}

const MIN_MENU_HEIGHT = 96
const VIEWPORT_PADDING = 8

const resolveAnchor = (input: AnchorInput): HTMLElement | undefined => (typeof input === 'function' ? input() : input)

const resolvePosition = (
    anchorRect: DOMRect,
    menuSize: { width: number; height: number },
    viewport: { w: number; h: number },
    placement: AnchoredMenuPlacement,
    offset: number,
): ResolvedPosition => {
    const [verticalPref, horizontalPref] = placement.split('-') as ['bottom' | 'top', 'start' | 'end']

    // Vertical
    const spaceBelow = viewport.h - anchorRect.bottom - offset - VIEWPORT_PADDING
    const spaceAbove = anchorRect.top - offset - VIEWPORT_PADDING
    const preferredSpace = verticalPref === 'bottom' ? spaceBelow : spaceAbove
    const otherSpace = verticalPref === 'bottom' ? spaceAbove : spaceBelow

    let resolvedVertical = verticalPref
    let availableSpace = preferredSpace
    if (menuSize.height > preferredSpace && otherSpace > preferredSpace) {
        resolvedVertical = verticalPref === 'bottom' ? 'top' : 'bottom'
        availableSpace = otherSpace
    }

    const maxHeight = Math.max(Math.min(menuSize.height, availableSpace), MIN_MENU_HEIGHT)
    const finalHeight = Math.min(menuSize.height, maxHeight)
    const top = resolvedVertical === 'bottom' ? anchorRect.bottom + offset : anchorRect.top - offset - finalHeight

    // Horizontal
    let resolvedHorizontal = horizontalPref
    let preferredLeft = horizontalPref === 'start' ? anchorRect.left : anchorRect.right - menuSize.width
    let overflowsRight = preferredLeft + menuSize.width > viewport.w - VIEWPORT_PADDING
    let overflowsLeft = preferredLeft < VIEWPORT_PADDING

    if (overflowsRight || overflowsLeft) {
        const flippedHorizontal: 'start' | 'end' = horizontalPref === 'start' ? 'end' : 'start'
        const flippedLeft = flippedHorizontal === 'start' ? anchorRect.left : anchorRect.right - menuSize.width
        const flippedOverflows =
            flippedLeft + menuSize.width > viewport.w - VIEWPORT_PADDING || flippedLeft < VIEWPORT_PADDING
        if (!flippedOverflows) {
            resolvedHorizontal = flippedHorizontal
            preferredLeft = flippedLeft
            overflowsRight = false
            overflowsLeft = false
        }
    }

    // Clamp so it never escapes the viewport horizontally
    const left = Math.max(VIEWPORT_PADDING, Math.min(preferredLeft, viewport.w - menuSize.width - VIEWPORT_PADDING))

    const transformOrigin = `${resolvedVertical === 'bottom' ? 'top' : 'bottom'} ${
        resolvedHorizontal === 'start' ? 'left' : 'right'
    }`

    return { top, left, maxHeight, minWidth: null, transformOrigin }
}

export function AnchoredMenu(props: AnchoredMenuProps) {
    // `mounted` lags `props.open` so the exit animation gets to play
    const [mounted, setMounted] = createSignal(props.open)
    const [closing, setClosing] = createSignal(false)
    const [pos, setPos] = createSignal<ResolvedPosition | null>(null)

    let wrapperEl: HTMLDivElement | undefined
    let menuEl: HTMLDivElement | undefined
    let animationPlacementLocked = false

    const offset = () => props.offset ?? 4
    const placement = (): AnchoredMenuPlacement => props.placement ?? 'bottom-start'

    const remeasure = () => {
        const anchor = resolveAnchor(props.anchor)
        if (!anchor || !menuEl) return
        const anchorRect = anchor.getBoundingClientRect()
        const menuRect = menuEl.getBoundingClientRect()
        // Use scrollHeight so the measurement reflects the natural content
        // extent even when a previously-applied max-height is currently
        // clamping the box (e.g. after items load asynchronously)
        const naturalHeight = menuEl.scrollHeight
        const next = resolvePosition(
            anchorRect,
            { width: menuRect.width, height: naturalHeight },
            { w: window.innerWidth, h: window.innerHeight },
            placement(),
            offset(),
        )
        if (props.matchAnchorWidth) next.minWidth = anchorRect.width
        // After the entry animation has transform-origin, only mutate the box position to avoid jumps mid-animation
        if (animationPlacementLocked) {
            const prev = pos()
            if (prev) next.transformOrigin = prev.transformOrigin
        }
        setPos(next)
    }

    // Mount immediately when open, schedule a remeasure next frame so the menu has computed size before we measure
    createEffect(
        on(
            () => props.open,
            isOpen => {
                if (isOpen) {
                    animationPlacementLocked = false
                    setClosing(false)
                    setMounted(true)
                    requestAnimationFrame(() => {
                        remeasure()
                        // Lock the transform-origin once measurement-driven
                        // placement is settled, before the entrance animation
                        // visually completes.
                        requestAnimationFrame(() => {
                            animationPlacementLocked = true
                        })
                    })
                } else if (mounted()) {
                    setClosing(true)
                }
            },
            { defer: true },
        ),
    )

    createEffect(() => {
        if (!props.open) return
        const onScroll = () => remeasure()
        const onResize = () => remeasure()
        const onPointer = (e: PointerEvent) => {
            const target = e.target as Node | null
            if (!target) return
            const anchor = resolveAnchor(props.anchor)
            if (menuEl?.contains(target)) return
            if (anchor?.contains(target)) return
            props.onClose()
        }
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                e.stopPropagation()
                props.onClose()
            }
        }

        window.addEventListener('scroll', onScroll, { capture: true, passive: true })
        window.addEventListener('resize', onResize, { passive: true })
        document.addEventListener('pointerdown', onPointer)
        document.addEventListener('keydown', onKey)

        let menuObserver: ResizeObserver | undefined
        let anchorObserver: ResizeObserver | undefined
        if (typeof ResizeObserver !== 'undefined') {
            menuObserver = new ResizeObserver(() => remeasure())
            anchorObserver = new ResizeObserver(() => remeasure())
            if (menuEl) menuObserver.observe(menuEl)
            const anchor = resolveAnchor(props.anchor)
            if (anchor) anchorObserver.observe(anchor)
        }

        // ResizeObserver only fires on size changes, not position changes
        // If an unrelated container causes the anchor to move without resizing, we won't be notified
        // So we poll for position changes every frame as a fallback
        let rafId: number | null = null
        let lastTop: number | null = null
        let lastLeft: number | null = null
        let lastScrollHeight: number | null = null
        const poll = () => {
            const anchor = resolveAnchor(props.anchor)
            let dirty = false
            if (anchor) {
                const r = anchor.getBoundingClientRect()
                if (r.top !== lastTop || r.left !== lastLeft) {
                    lastTop = r.top
                    lastLeft = r.left
                    dirty = true
                }
            }
            if (menuEl) {
                const sh = menuEl.scrollHeight
                if (sh !== lastScrollHeight) {
                    lastScrollHeight = sh
                    dirty = true
                }
            }
            if (dirty) remeasure()
            rafId = requestAnimationFrame(poll)
        }
        rafId = requestAnimationFrame(poll)

        onCleanup(() => {
            window.removeEventListener('scroll', onScroll, { capture: true })
            window.removeEventListener('resize', onResize)
            document.removeEventListener('pointerdown', onPointer)
            document.removeEventListener('keydown', onKey)
            menuObserver?.disconnect()
            anchorObserver?.disconnect()
            if (rafId !== null) cancelAnimationFrame(rafId)
        })
    })

    const handleAnimationEnd = (e: AnimationEvent) => {
        if (e.target !== wrapperEl) return
        if (closing()) {
            setClosing(false)
            setMounted(false)
            setPos(null)
        }
    }

    return (
        <Show when={mounted()}>
            <div
                ref={el => (wrapperEl = el)}
                class={`${styles.wrapper} ${closing() ? styles.closing : styles.opening}`}
                style={{
                    top: pos() ? `${pos()!.top}px` : '-9999px',
                    left: pos() ? `${pos()!.left}px` : '-9999px',
                    visibility: pos() ? 'visible' : 'hidden',
                    'transform-origin': pos()?.transformOrigin ?? 'top left',
                }}
                onAnimationEnd={handleAnimationEnd}
            >
                <Menu
                    ref={el => (menuEl = el)}
                    class={props.class}
                    role="menu"
                    style={{
                        'max-height': pos() ? `${pos()!.maxHeight}px` : undefined,
                        'overflow-y': 'auto',
                        ...(pos()?.minWidth ? { 'min-width': `${pos()!.minWidth}px` } : {}),
                    }}
                >
                    {props.children}
                </Menu>
            </div>
        </Show>
    )
}
