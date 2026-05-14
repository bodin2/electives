import CloseIcon from '@iconify-icons/mdi/close'
import { ListItem, mergeClasses } from 'm3-solid/src'
import { type Component, Show } from 'solid-js'
import { GroupType, type User } from '~/api'
import AvatarPlaceholder from '~/images/avatar-placeholder.webp'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import Badge from '../Badge'
import { Badges } from '../Badges'
import { Button } from '../Button'
import { HStack } from '../Stack'
import styles from './UserListItem.module.css'

export interface UserListItemProps {
    user: User
    showId?: boolean
    currentUser?: User
    onClick?: () => unknown
    /**
     * Pass to show a remove button on the right side of the item
     */
    onRemove?: () => unknown
    removeDisabled?: boolean
    class?: string
    /**
     * A custom component to show on the right side of the item.
     * Overrides the remove button if `onRemove` is also provided.
     */
    trailing?: Component<{ user: User }>
    /** Whether this item is visually selected. */
    selected?: boolean
    /** Whether this item is disabled (non-interactive). */
    disabled?: boolean
    /** If true, show the grade group badge. */
    showGradeGroup?: boolean
}

export function UserListItem(props: UserListItemProps) {
    const { string } = useI18n()

    const groups = () =>
        props.showGradeGroup ? props.user.groups : props.user.groups.filter(g => g.type !== GroupType.GRADE)

    return (
        <ListItem
            class={mergeClasses(props.selected && styles.selected, props.disabled && styles.disabled, props.class)}
            onClick={props.disabled ? undefined : props.onClick}
            lines={4}
            leading={
                <img class={styles.avatar} src={props.user.avatarUrl || AvatarPlaceholder} alt={string.AVATAR()} />
            }
            headline={
                <HStack alignVertical="center" wrap style={{ 'row-gap': '4px' }}>
                    {props.user.displayName}
                    <Badges groups={groups()} />
                    <Show when={props.user.id === props.currentUser?.id}>
                        <Badge variant="tertiary">{string.YOU()}</Badge>
                    </Show>
                </HStack>
            }
            supporting={props.showId && props.user.id}
            trailing={
                <Show
                    when={props.trailing}
                    fallback={
                        <Show when={props.onRemove}>
                            <Button
                                disabled={props.removeDisabled}
                                size="xs"
                                aria-label={string.REMOVE_STUDENT_FROM_SUBJECT()}
                                variant="tonal-error"
                                onClick={async e => {
                                    e.stopPropagation()
                                    await nonNull(props.onRemove)()
                                }}
                                icon={CloseIcon}
                                iconType="only"
                            />
                        </Show>
                    }
                >
                    {trailing => trailing()({ user: props.user })}
                </Show>
            }
        />
    )
}
