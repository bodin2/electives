import { Icon, mergeClasses } from 'm3-solid/src'
import styles from './ContainedIcon.module.css'
import { VStack } from './Stack'
import type { IconifyIcon } from '@iconify/types'

export function ContainedIcon(props: {
    icon: IconifyIcon
    class?: string
    containerColor?: string
    iconColor?: string
}) {
    return (
        <VStack
            alignHorizontal="center"
            alignVertical="center"
            class={mergeClasses(styles.container, props.class)}
            style={{
                background: props.containerColor || 'var(--m3c-secondary-container)',
            }}
        >
            <Icon icon={props.icon} size={24} style={{ color: props.iconColor }} />
        </VStack>
    )
}
