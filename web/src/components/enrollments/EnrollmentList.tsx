import { For, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { groupItems } from '../../utils'
import { HStack } from '../Stack'
import EnrollmentCard from './EnrollmentCard'
import styles from './EnrollmentList.module.css'
import type { Enrollment, User } from '../../api'

export interface EnrollmentListProps {
    enrollments: Enrollment[]
    user?: User
    onCardClick: (id: number) => void
}

export default function EnrollmentList(props: EnrollmentListProps) {
    const api = useAPI()

    const groupedEnrollments = () =>
        groupItems(props.enrollments, enrollment => {
            if (props.user && api.client.selections.resolveSelection(props.user.id, enrollment.id))
                return 'selected' as const
            return 'unselected' as const
        })

    return (
        <>
            <Show when={groupedEnrollments().unselected?.length}>
                <HStack gap={16} class="padded" wrap>
                    <For each={groupedEnrollments().unselected}>
                        {enrollment => (
                            <EnrollmentCard enrollment={enrollment} class={styles.card} onClick={props.onCardClick} />
                        )}
                    </For>
                </HStack>
            </Show>
            <Show when={groupedEnrollments().selected?.length}>
                <HStack gap={16} class="padded" wrap>
                    <For each={groupedEnrollments().selected}>
                        {enrollment => (
                            <EnrollmentCard enrollment={enrollment} class={styles.card} onClick={props.onCardClick} />
                        )}
                    </For>
                </HStack>
            </Show>
        </>
    )
}
