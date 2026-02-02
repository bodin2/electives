import { Icon } from 'm3-solid'
import { HStack } from '../Stack'
import type { IconifyIcon } from '@iconify/types'

interface IconLabelProps {
    icon: IconifyIcon
    text: string
    class?: string
    iconSize?: number
}

export default function IconLabel(props: IconLabelProps) {
    const size = () => props.iconSize ?? 20
    return (
        <HStack alignVertical="center" gap={4} class={props.class}>
            <Icon icon={props.icon} width={size()} height={size()} />
            <span>{props.text}</span>
        </HStack>
    )
}
