import { Icon } from 'm3-solid'
import { HStack } from '../Stack'
import type { IconifyIcon } from '@iconify/types'

interface IconLabelProps {
    icon: IconifyIcon
    text: string
    class?: string
}

export default function IconLabel(props: IconLabelProps) {
    return (
        <HStack alignVertical="center" gap={4} class={props.class}>
            <Icon icon={props.icon} width={20} height={20} />
            <span>{props.text}</span>
        </HStack>
    )
}
