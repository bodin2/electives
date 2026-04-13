import { For, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { groupItems } from '../../utils'
import { HStack } from '../Stack'
import ElectiveCard from './ElectiveCard'
import styles from './ElectiveList.module.css'
import type { Elective, User } from '../../api'

export interface ElectiveListProps {
    electives: Elective[]
    user?: User
    onCardClick: (id: number) => void
}

export default function ElectiveList(props: ElectiveListProps) {
    const api = useAPI()

    const groupedElectives = () =>
        groupItems(props.electives, elective => {
            if (props.user && api.client.selections.resolveSelection(props.user.id, elective.id))
                return 'selected' as const
            return 'unselected' as const
        })

    return (
        <>
            <Show when={groupedElectives().unselected?.length}>
                <HStack gap={16} class="padded" wrap>
                    <For each={groupedElectives().unselected}>
                        {elective => (
                            <ElectiveCard elective={elective} class={styles.card} onClick={props.onCardClick} />
                        )}
                    </For>
                </HStack>
            </Show>
            <Show when={groupedElectives().selected?.length}>
                <HStack gap={16} class="padded" wrap>
                    <For each={groupedElectives().selected}>
                        {elective => (
                            <ElectiveCard elective={elective} class={styles.card} onClick={props.onCardClick} />
                        )}
                    </For>
                </HStack>
            </Show>
        </>
    )
}
