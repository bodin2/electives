import HashTagIcon from '@iconify-icons/mdi/hashtag-box-outline'
import LabelOutlineIcon from '@iconify-icons/mdi/label-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import { Show } from 'solid-js'
import { GroupType, UserType } from '~/api/types'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import { Badges, GroupBadge } from '../Badges'
import { Button } from '../Button'
import IconLabel from '../IconLabel'
import { HStack, VStack } from '../Stack'
import UserAvatar from './UserAvatar'
import { useUserInfoContext } from './UserInfo'
import styles from './UserProfile.module.css'
import type { User } from '~/api/structures'

export function UserProfile(props: {
    editAvatar?: () => void
    onAddGroupClick?: () => void
    onEditGroup?: (slot: GroupType) => void
}) {
    const { string } = useI18n()
    const ctx = useUserInfoContext()

    const user = () => nonNull(ctx.user)

    const userTypeName = () => {
        const type = user().type
        const key = UserType[type]
        if (!key) return string.ERROR()
        // @ts-expect-error: Dynamic key
        return string[`USER_TYPE_${key}`]()
    }

    return (
        <HStack gap={32} alignVertical="center">
            {/** biome-ignore lint/a11y/noStaticElementInteractions: Intentional */}
            {/** biome-ignore lint/a11y/useKeyWithClickEvents: Intentional */}
            <div
                style={{
                    position: 'relative',
                    width: 'fit-content',
                    cursor: props.editAvatar ? 'pointer' : 'default',
                }}
                onClick={props.editAvatar}
            >
                <UserAvatar
                    imageUrl={user().avatarUrl}
                    class={styles.avatar}
                    placeholderClass={`${styles.avatar} ${styles.placeholder}`}
                />
                <Show when={props.editAvatar}>
                    <Button
                        size="xs"
                        variant="tonal"
                        icon={PencilOutlineIcon}
                        iconType="only"
                        onClick={props.editAvatar}
                        style={{ position: 'absolute', bottom: 0, right: 0 }}
                    />
                </Show>
            </div>

            <VStack grow>
                <HStack alignVertical="center" wrap>
                    <h1 class="m3-headline-medium">{user().displayName}</h1>
                    <HStack wrap style={{ 'row-gap': '4px' }}>
                        <Show when={user().isStudent()}>
                            <FixedSlotBadge user={user()} slot={GroupType.GRADE} onEdit={props.onEditGroup} required />
                            <FixedSlotBadge user={user()} slot={GroupType.ROOM} onEdit={props.onEditGroup} required />
                            <FixedSlotBadge user={user()} slot={GroupType.PROGRAM} onEdit={props.onEditGroup} />
                        </Show>
                        <BadgeListEditor user={user()} />
                        <Show when={props.onAddGroupClick}>
                            {onAddGroupClick => (
                                <HStack gap={4} alignVertical="center" wrap>
                                    <Button size="xs" variant="tonal" icon={PlusIcon} onClick={onAddGroupClick()}>
                                        {string.ADD_GROUP()}
                                    </Button>
                                </HStack>
                            )}
                        </Show>
                    </HStack>
                </HStack>

                <HStack class={styles.infoRow}>
                    <Show when={!ctx.creating}>
                        <IconLabel icon={HashTagIcon} text={String(user().id)} class={styles.labelSubText} />
                        <IconLabel icon={LabelOutlineIcon} text={userTypeName()} class={styles.labelSubText} />
                    </Show>
                </HStack>
            </VStack>
        </HStack>
    )
}

function BadgeListEditor(props: { user: User }) {
    const ctx = useUserInfoContext()

    return (
        <Badges
            groups={props.user.groups}
            types={props.user.isTeacher() ? undefined : [GroupType.CUSTOM]}
            onRemove={
                ctx.editable && ctx.onEdit
                    ? group =>
                          nonNull(ctx.onEdit)(
                              'groups',
                              props.user.groups.filter(g => g.id !== group.id).map(g => g.toJSON()),
                              'patchGroups',
                          )
                    : undefined
            }
        />
    )
}

function FixedSlotBadge(props: {
    user: User
    slot: GroupType
    required?: boolean
    onEdit?: (slot: GroupType) => void
}) {
    const { string } = useI18n()
    const ctx = useUserInfoContext()
    const current = () => props.user.groups.find(g => g.type === props.slot)

    return (
        <Show when={ctx.editable || current()}>
            <GroupBadge
                group={current()}
                fallbackType={props.slot}
                placeholder={slotPlaceholder(props.slot, string)}
                required={props.required}
                onEdit={ctx.editable && props.onEdit ? () => nonNull(props.onEdit)(props.slot) : undefined}
            />
        </Show>
    )
}

export type StringApi = ReturnType<typeof useI18n>['string']

export function slotPlaceholder(slot: GroupType | null, string: StringApi): string {
    switch (slot) {
        case GroupType.GRADE:
            return string.SELECT_GRADE_HINT()
        case GroupType.ROOM:
            return string.SELECT_ROOM_HINT()
        case GroupType.PROGRAM:
            return string.SELECT_PROGRAM_HINT()
        default:
            return string.SELECT_GROUP_HINT()
    }
}
