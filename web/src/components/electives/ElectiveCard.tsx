import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import { useNavigate } from '@tanstack/solid-router'
import { Card, Icon } from 'm3-solid'
import { createSignal, Show } from 'solid-js'
import useElectiveOpen from '../../hooks/useElectiveOpen'
import { useI18n } from '../../providers/I18nProvider'
import { formatCountdown } from '../../utils/date'
import { HStack, VStack } from '../Stack'
import type { Elective } from '../../api'

interface ElectiveCardProps {
    elective: Elective
    cardClass?: string
}

export default function ElectiveCard(props: ElectiveCardProps) {
    const { string, locale } = useI18n()
    const navigate = useNavigate()
    const [countdown, setCountdown] = createSignal<number | null>(null)
    const isOpen = useElectiveOpen(props.elective, {
        onCountdown: timeRemaining => setCountdown(timeRemaining),
    })

    const formatDuration = (date: Date) => {
        return new Intl.DateTimeFormat(locale(), {
            dateStyle: 'medium',
            timeStyle: 'short',
        }).format(date)
    }

    const formatDateRange = () => {
        const { startDate, endDate } = props.elective
        if (isOpen()) {
            if (endDate) return string.ENROLLMENT_CLOSES_AT({ duration: formatDuration(endDate) })
        } else {
            const countdownText = formatCountdown(countdown())
            if (countdownText) return string.ENROLLMENT_CLOSED_OPENING_IN({ time: countdownText })
            if (startDate) return string.ENROLLMENT_OPENS_AT({ duration: formatDuration(startDate) })
        }
        return null
    }

    return (
        <Card
            variant={isOpen() ? 'elevated' : 'outlined'}
            data-open={isOpen()}
            class={props.cardClass}
            onClick={() =>
                navigate({
                    to: '/enroll/$electiveId',
                    params: { electiveId: props.elective.id },
                })
            }
        >
            <HStack alignHorizontal="space-between" alignVertical="center" gap={16}>
                <VStack gap={4}>
                    <h1 class="m3-title-large">{props.elective.name}</h1>
                    <VStack gap={2}>
                        <p class="m3-body-medium">{isOpen() ? string.ENROLLMENT_OPEN() : string.ENROLLMENT_CLOSED()}</p>
                        <Show when={formatDateRange()}>
                            <p class="m3-body-small">{formatDateRange()}</p>
                        </Show>
                    </VStack>
                </VStack>
                <Show when={isOpen()}>
                    <Icon width={24} height={24} icon={ArrowRightIcon} />
                </Show>
            </HStack>
        </Card>
    )
}
