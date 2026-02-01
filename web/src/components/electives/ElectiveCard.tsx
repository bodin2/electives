import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import { useNavigate } from '@tanstack/solid-router'
import { Card, Icon } from 'm3-solid'
import { Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { HStack, VStack } from '../Stack'
import type { Elective } from '../../api'

interface ElectiveCardProps {
    elective: Elective
    cardClass?: string
}

export default function ElectiveCard(props: ElectiveCardProps) {
    const { string } = useI18n()
    const navigate = useNavigate()

    const isOpen = () => props.elective.isSelectionOpen()

    return (
        <Card
            variant={isOpen() ? 'elevated' : 'outlined'}
            data-open={isOpen()}
            class={props.cardClass}
            onClick={
                isOpen()
                    ? () =>
                          navigate({
                              to: '/enroll/$electiveId',
                              params: { electiveId: props.elective.id },
                          })
                    : undefined
            }
        >
            <HStack alignHorizontal="space-between" alignVertical="center" gap={16}>
                <VStack gap={4}>
                    <h1 class="m3-title-large">{props.elective.name}</h1>
                    <p class="m3-body-medium">{isOpen() ? string.ENROLLMENT_OPEN() : string.ENROLLMENT_CLOSED()}</p>
                </VStack>
                <Show when={isOpen()}>
                    <Icon width={24} height={24} icon={ArrowRightIcon} />
                </Show>
            </HStack>
        </Card>
    )
}
