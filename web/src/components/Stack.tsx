import { type ComponentProps, type JSX, type JSXElement, splitProps } from 'solid-js'
import { Dynamic } from 'solid-js/web'
import styles from './Stack.module.css'
import type { StyleRecordOnly } from '../global'

export function VStack<const As extends keyof JSX.HTMLElementTags>(props: StackProps<As>) {
    const [local, others] = splitProps(props, [
        'class',
        'classList',
        'children',
        'alignVertical',
        'alignHorizontal',
        'wrap',
        'style',
        'gap',
        'grow',
        'as',
    ])

    return (
        <Dynamic
            component={local.as ?? 'div'}
            {...others}
            class={`${styles.stack} ${styles.vert}`}
            classList={{
                ...local.classList,
                [styles.wrap]: local.wrap,
                [local.class ?? '']: Boolean(local.class),
            }}
            style={{
                'align-items': getChildrenAlignmentValue(local.alignHorizontal),
                'justify-content': getChildrenAlignmentValue(local.alignVertical),
                'flex-grow': local.grow ? '1' : undefined,
                gap: getGap(local.gap),
                ...(local.style as JSX.CSSProperties),
            }}
        >
            {local.children}
        </Dynamic>
    )
}

export function HStack<const As extends keyof JSX.HTMLElementTags>(props: StackProps<As>) {
    const [local, others] = splitProps(props, [
        'class',
        'classList',
        'children',
        'alignVertical',
        'alignHorizontal',
        'wrap',
        'style',
        'gap',
        'grow',
        'as',
    ])

    return (
        <Dynamic
            component={local.as ?? 'div'}
            {...others}
            class={styles.stack}
            classList={{
                ...local.classList,
                [styles.wrap]: local.wrap,
                [local.class ?? '']: Boolean(local.class),
            }}
            style={{
                'align-items': getChildrenAlignmentValue(local.alignVertical),
                'justify-content': getChildrenAlignmentValue(local.alignHorizontal),
                'flex-grow': local.grow ? '1' : undefined,
                gap: getGap(local.gap),
                ...(local.style as JSX.CSSProperties),
            }}
        >
            {local.children}
        </Dynamic>
    )
}

export type ChildrenAlignment = 'start' | 'center' | 'end' | 'stretch' | 'space-between'

function getChildrenAlignmentValue(alignment: ChildrenAlignment | undefined) {
    switch (alignment) {
        case 'start':
            return 'flex-start'
        case 'end':
            return 'flex-end'
        case 'stretch':
        case 'center':
        case 'space-between':
            return alignment
        default:
            return undefined
    }
}

function getGap(gap: string | number | undefined) {
    if (typeof gap === 'number') {
        return `${gap}px`
    }

    return gap ?? '8px'
}

type StackProps<As extends keyof JSX.HTMLElementTags = 'div'> = Omit<
    StyleRecordOnly<ComponentProps<As>>,
    'class' | 'classList'
> & {
    as?: As
    class?: string
    classList?: Record<string, boolean | undefined>
    children?: JSXElement
    alignVertical?: ChildrenAlignment
    alignHorizontal?: ChildrenAlignment
    wrap?: boolean
    gap?: string | number
    grow?: boolean
}
