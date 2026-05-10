import { Card } from 'm3-solid'
import { For } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { nonNull } from '../../utils'
import Badge from '../Badge'
import LogOutButton from '../buttons/LogOutButton'
import { HStack, VStack } from '../Stack'
import UserAvatar from './UserAvatar'
import styles from './UserInfoCard.module.css'

interface UserInfoCardProps {
    class?: string
}

export default function UserInfoCard(props: UserInfoCardProps) {
    const api = useAPI()
    const user = () => nonNull(api.client.user)

    return (
        <Card variant="outlined" class={props.class}>
            <VStack gap={16}>
                <HStack alignHorizontal="space-between" class={styles.userInfo}>
                    <VStack>
                        <p class="m3-title-large">{user().displayName}</p>
                        <HStack alignVertical="center" gap={4} style={{ 'row-gap': '2px' }} wrap>
                            <For each={user().groups}>
                                {group => (
                                    <>
                                        <Badge variant="tonal">{group.name}</Badge>{' '}
                                    </>
                                )}
                            </For>
                        </HStack>
                    </VStack>
                    <UserAvatar imageUrl={user().avatarUrl} class={styles.avatar} />
                </HStack>
                <LogOutButton />
            </VStack>
        </Card>
    )
}
