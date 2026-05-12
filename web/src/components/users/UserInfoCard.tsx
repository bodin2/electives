import { Card } from 'm3-solid'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { Badges } from '../Badges'
import LogOutButton from '../buttons/LogOutButton'
import { HStack, VStack } from '../Stack'
import UserAvatar from './UserAvatar'
import styles from './UserInfoCard.module.css'

interface UserInfoCardProps {
    class?: string
}

export default function UserInfoCard(props: UserInfoCardProps) {
    const api = useAPI()
    const { string } = useI18n()
    const user = () => nonNull(api.client.user)

    return (
        <Card variant="outlined" class={props.class}>
            <VStack as="section" aria-label={string.ACCOUNT_AND_SETTINGS()} gap={16}>
                <HStack alignHorizontal="space-between" class={styles.userInfo}>
                    <VStack>
                        <h1 class="m3-title-large">{user().displayName}</h1>
                        <HStack
                            as="ul"
                            aria-label={string.GROUPS()}
                            alignVertical="center"
                            gap={4}
                            style={{ 'row-gap': '2px' }}
                            wrap
                        >
                            <Badges groups={user().groups} />
                        </HStack>
                    </VStack>
                    <UserAvatar imageUrl={user().avatarUrl} class={styles.avatar} />
                </HStack>
                <LogOutButton />
            </VStack>
        </Card>
    )
}
