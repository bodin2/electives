import { Icon } from 'm3-solid'
import { HStack } from './Stack'
import type { IconifyIcon } from '@iconify/types'
import type { JSXElement } from 'solid-js'

interface IconLabelProps {
    icon: IconifyIcon
    text: JSXElement
    class?: string
    iconSize?: number
    'aria-label'?: string
}

export default function IconLabel(props: IconLabelProps) {
    const size = () => props.iconSize ?? 20
    return (
        <HStack alignVertical="center" gap={4} class={props.class} aria-label={props['aria-label']} role="paragraph">
            <Icon icon={props.icon} width={size()} height={size()} />
            <span style="color: inherit; font: inherit" aria-hidden={Boolean(props['aria-label'])}>
                {props.text}
            </span>
        </HStack>
    )
}
