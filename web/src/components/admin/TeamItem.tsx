import DeleteOutlineIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { ListItem } from 'm3-solid'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { HStack } from '../Stack'
import type { Team } from '../../api/structures'

interface TeamItemProps {
    team: Team
    memberCount: number
    onEdit: (team: Team) => void
    onDelete: (team: Team) => void
}

export default function TeamItem(props: TeamItemProps) {
    const { string } = useI18n()

    return (
        <ListItem
            headline={props.team.name}
            supporting={string.MEMBER_COUNT({ count: props.memberCount })}
            onClick={() => props.onEdit(props.team)}
            trailing={
                <HStack gap={8}>
                    <Button
                        variant="text"
                        iconType="only"
                        icon={PencilOutlineIcon}
                        aria-label={string.EDIT_TEAM()}
                        onClick={e => {
                            e.stopPropagation()
                            props.onEdit(props.team)
                        }}
                    />
                    <Button
                        variant="tonal-error"
                        iconType="only"
                        icon={DeleteOutlineIcon}
                        aria-label={string.DELETE_TEAM()}
                        onClick={e => {
                            e.stopPropagation()
                            props.onDelete(props.team)
                        }}
                    />
                </HStack>
            }
        />
    )
}
