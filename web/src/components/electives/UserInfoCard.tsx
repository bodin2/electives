import LogOutIcon from '@iconify-icons/mdi/logout'
import { Card } from 'm3-solid'
import { For } from 'solid-js'
import AvatarPlaceholder from '../../images/avatar-placeholder.webp'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import Badge from '../Badge'
import { Button } from '../Button'
import { HStack, VStack } from '../Stack'

interface UserInfoCardProps {
    cardClass?: string
    avatarClass?: string
}

export default function UserInfoCard(props: UserInfoCardProps) {
    const api = useAPI()
    const { string } = useI18n()
    const user = () => nonNull(api.client.user)

    return (
        <Card variant="outlined" class={props.cardClass}>
            <VStack gap={16}>
                <HStack alignHorizontal="space-between">
                    <VStack>
                        <p class="m3-title-large">{user().fullName}</p>
                        <HStack alignVertical="center" gap={4} style={{ 'row-gap': '2px' }} wrap>
                            <For each={user().teams}>
                                {team => (
                                    <>
                                        <Badge variant="tonal">{team.name}</Badge>{' '}
                                    </>
                                )}
                            </For>
                        </HStack>
                    </VStack>
                    <img src={user().avatarUrl || AvatarPlaceholder} class={props.avatarClass} alt={string.AVATAR()} />
                </HStack>
                <Button icon={LogOutIcon} variant="tonal-error" onClick={() => api.logout()}>
                    {string.LOGOUT()}
                </Button>
            </VStack>
        </Card>
    )
}
