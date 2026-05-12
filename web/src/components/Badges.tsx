import AddIcon from '@iconify-icons/mdi/add'
import CloseIcon from '@iconify-icons/mdi/close'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { Icon } from 'm3-solid/src'
import { For, type JSX, Show } from 'solid-js'
import { type Group, GroupType } from '../api'
import { nonNull } from '../utils'
import { GROUP_TYPE_ICONS } from './admin/GroupItem'
import Badge from './Badge'
import styles from './Badges.module.css'
import { Button } from './Button'
import type { IconifyIcon } from '@iconify/types'

export interface GroupBadgeProps {
    group?: Group
    fallbackType?: GroupType
    placeholder?: JSX.Element
    /** When true and the placeholder is shown, append a red asterisk. */
    required?: boolean
    /** Renders an X button. Click removes/clears this group. */
    onRemove?: () => void
    /**
     * Renders a trailing action button.
     *
     * Defaults to a pencil icon when `group` is defined and a plus icon when it's a placeholder.
     * Pass {@link editIcon} to override.
     */
    onEdit?: () => void
    editIcon?: IconifyIcon
    /** Make the whole badge clickable. */
    onClick?: () => void
    /**
     * Leading icon size in px
     * @default 16
     */
    iconSize?: number
}

export function GroupBadge(props: GroupBadgeProps) {
    const leadingIcon = () => GROUP_TYPE_ICONS[props.group?.type ?? props.fallbackType ?? GroupType.CUSTOM]
    const editIcon = () => props.editIcon ?? (props.group ? PencilOutlineIcon : AddIcon)
    const iconSize = () => props.iconSize ?? 16

    return (
        <Badge
            variant="tonal"
            class={styles.badge}
            onClick={props.onClick}
            style={props.onClick ? { cursor: 'pointer' } : undefined}
        >
            <Show when={leadingIcon()}>{i => <Icon icon={i()} size={iconSize()} />}</Show>
            <Show
                when={props.group}
                fallback={
                    <>
                        {props.placeholder}
                        <Show when={props.required}>
                            <span class="text-error">*</span>
                        </Show>
                    </>
                }
            >
                {g => <>{g().name}</>}
            </Show>
            <Show when={props.onEdit}>
                {handler => (
                    <Button
                        size="xs"
                        variant="text"
                        icon={editIcon()}
                        iconType="only"
                        class={styles.actionButton}
                        onClick={e => {
                            e.stopPropagation()
                            handler()()
                        }}
                    />
                )}
            </Show>
            <Show when={props.onRemove}>
                {handler => (
                    <Button
                        size="xs"
                        variant="text"
                        icon={CloseIcon}
                        iconType="only"
                        class={styles.actionButton}
                        onClick={e => {
                            e.stopPropagation()
                            handler()()
                        }}
                    />
                )}
            </Show>
        </Badge>
    )
}

export interface BadgesProps {
    groups: Group[]
    /** Filter to only these types. Default: all types. */
    types?: GroupType[]
    /** If provided, each badge gets an X button that calls this with the group. */
    onRemove?: (group: Group) => void
}

export function Badges(props: BadgesProps) {
    const visible = () => (props.types ? props.groups.filter(g => nonNull(props.types).includes(g.type)) : props.groups)

    return (
        <For each={visible()}>
            {group => (
                <GroupBadge
                    group={group}
                    onRemove={props.onRemove ? () => nonNull(props.onRemove)(group) : undefined}
                />
            )}
        </For>
    )
}
