import AccountGraduationOutlineIcon from '@iconify-icons/mdi/account-graduation-outline'
import AccountGroupOutlineIcon from '@iconify-icons/mdi/account-group-outline'
import BookEducationOutlineIcon from '@iconify-icons/mdi/book-education-outline'
import BuildingIcon from '@iconify-icons/mdi/building'
import DeleteOutlineIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { ListItem } from 'm3-solid/src'
import { Show } from 'solid-js'
import { GroupType } from '~/api'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../Button'
import { ContainedIcon } from '../ContainedIcon'
import { HStack } from '../Stack'
import type { IconifyIcon } from '@iconify/types'
import type { Group } from '~/api/structures'

interface GroupItemProps {
    group: Group
    memberCount: number
    onEdit: (group: Group) => void
    onDelete: (group: Group) => void
}

export const GROUP_TYPE_ICONS: Record<GroupType, IconifyIcon | undefined> = {
    [GroupType.CUSTOM]: AccountGroupOutlineIcon,
    [GroupType.GRADE]: AccountGraduationOutlineIcon,
    [GroupType.ROOM]: BuildingIcon,
    [GroupType.PROGRAM]: BookEducationOutlineIcon,
    [GroupType.UNRECOGNIZED]: undefined,
}

export default function GroupItem(props: GroupItemProps) {
    const { string } = useI18n()

    // The server refuses to delete a non-CUSTOM/PROGRAM group that still has members,
    // as it would silently break the GRADE/ROOM invariant for those students.
    // Hide the delete affordance in that case so the UI matches server policy.
    const canDelete = () => [GroupType.CUSTOM, GroupType.PROGRAM].includes(props.group.type) || props.memberCount === 0

    const typeLabel = () => {
        const key = GroupType[props.group.type]
        if (key === 'UNRECOGNIZED') return undefined
        // @ts-expect-error: Dynamic keys
        return string[`GROUP_TYPE_${key}`]()
    }

    return (
        <ListItem
            headline={props.group.name}
            overline={typeLabel()}
            supporting={string.MEMBER_COUNT({ count: props.memberCount })}
            onClick={() => props.onEdit(props.group)}
            leading={<Show when={GROUP_TYPE_ICONS[props.group.type]}>{icon => <ContainedIcon icon={icon()} />}</Show>}
            trailing={
                <HStack gap={8}>
                    <Button
                        variant="text"
                        iconType="only"
                        icon={PencilOutlineIcon}
                        aria-label={string.EDIT_GROUP()}
                        onClick={e => {
                            e.stopPropagation()
                            props.onEdit(props.group)
                        }}
                    />
                    <Button
                        disabled={!canDelete()}
                        variant="tonal-error"
                        iconType="only"
                        icon={DeleteOutlineIcon}
                        aria-label={string.DELETE_GROUP()}
                        onClick={e => {
                            e.stopPropagation()
                            props.onDelete(props.group)
                        }}
                    />
                </HStack>
            }
        />
    )
}
