import { useScrollData } from '~/providers/ScrollDataProvider'
import styles from './BottomBar.module.css'
import { VStack } from './Stack'
import type { ParentProps } from 'solid-js'

export { default as bottomBarStyles } from './BottomBar.module.css'

export default function BottomBar(props: ParentProps) {
    const scrollData = useScrollData()

    return (
        <VStack
            alignHorizontal="center"
            class={styles.container}
            style={{
                'border-top-color':
                    scrollData.maxScrollY - scrollData.scrollY > 16 ? 'var(--m3c-outline-variant)' : undefined,
            }}
        >
            {props.children}
        </VStack>
    )
}
