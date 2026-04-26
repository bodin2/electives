import { Tabs } from 'm3-solid'
import { useScrollData } from '../providers/ScrollDataProvider'
import styles from './StickyTabs.module.css'
import type { ComponentProps } from 'solid-js'

export type StickyTabsProps = ComponentProps<typeof Tabs>

export default function StickyTabs(props: StickyTabsProps) {
    const scrollData = useScrollData()

    return (
        <Tabs
            {...props}
            class={`${styles.tabs} ${props.class ?? ''}`}
            style={{
                'outline-color': scrollData.scrolledVertical ? 'var(--m3c-outline-variant)' : undefined,
                '--m3-tabs-container-color': scrollData.scrolledVertical ? 'var(--m3c-surface-container)' : undefined,
                ...(typeof props.style === 'object' ? props.style : {}),
            }}
        />
    )
}
