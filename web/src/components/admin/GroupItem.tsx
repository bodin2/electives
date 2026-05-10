import DeleteOutlineIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { ListItem } from 'm3-solid'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { HStack } from '../Stack'
import type { Group } from '../../api/structures'

interface GroupItemProps {
    group: Group
    memberCount: number
    onEdit: (group: Group) => void
    onDelete: (group: Group) => void
}

export default function GroupItem(props: GroupItemProps) {
    const { string } = useI18n()

    return (
        <ListItem
            headline={props.group.name}
            supporting={string.MEMBER_COUNT({ count: props.memberCount })}
            onClick={() => props.onEdit(props.group)}
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
