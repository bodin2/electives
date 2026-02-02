import { mergeClasses } from 'm3-solid'
import styles from './Badge.module.css'
import type { ComponentProps } from 'solid-js'

interface BadgeProps extends ComponentProps<'span'> {
    variant?: 'error' | 'tonal' | 'tertiary'
}

export default function Badge(props: BadgeProps) {
    return (
        <span class={mergeClasses(styles.badge, styles[props.variant ?? 'error'], 'm3-label-large', props.class)}>
            {props.children}
        </span>
    )
}
