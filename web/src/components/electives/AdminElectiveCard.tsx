import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import CalendarIcon from '@iconify-icons/mdi/calendar-clock-outline'
import PeopleIcon from '@iconify-icons/mdi/people-outline'
import { createQuery } from '@tanstack/solid-query'
import { Card, mergeClasses } from 'm3-solid'
import { Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { teamsQueryOptions } from '../../queries/teams'
import { formatDuration } from '../../utils/date'
import IconLabel from '../IconLabel'
import { HStack, VStack } from '../Stack'
import styles from './AdminElectiveCard.module.css'
import type { Elective } from '../../api'

interface AdminElectiveCardProps {
    elective: Elective
    class?: string
    onClick: (id: number) => void
}

export default function AdminElectiveCard(props: AdminElectiveCardProps) {
    const { string, locale } = useI18n()
    const { client } = useAPI()

    const teamsQuery = createQuery(() => teamsQueryOptions(client))

    const startDateStr = () =>
        props.elective.startDate ? formatDuration(locale, props.elective.startDate) : string.NOT_SET()
    const endDateStr = () =>
        props.elective.endDate ? formatDuration(locale, props.elective.endDate) : string.NOT_SET()

    const teamName = () => {
        if (props.elective.teamId === null || !teamsQuery.data) return undefined
        return teamsQuery.data.find(t => t.id === props.elective.teamId)?.name
    }

    return (
        <Card
            variant="outlined"
            data-open={props.elective.isSelectionOpen()}
            class={mergeClasses(props.class, styles.card)}
            onClick={() => props.onClick(props.elective.id)}
        >
            <VStack gap={8} grow>
                <h1 class="m3-title-large text-balance">{props.elective.name}</h1>

                <VStack gap={4} style={{ color: 'var(--m3c-on-surface-variant)' }}>
                    <HStack alignVertical="center" gap={4}>
                        <IconLabel icon={CalendarIcon} text={startDateStr()} iconSize={16} class="m3-body-medium" />
                        <IconLabel icon={ArrowRightIcon} text={endDateStr()} iconSize={16} class="m3-body-medium" />
                    </HStack>

                    <Show when={teamName()}>
                        {name => (
                            <IconLabel
                                icon={PeopleIcon}
                                text={`${string.TEAM()}: ${name()}`}
                                iconSize={16}
                                class="m3-body-medium"
                            />
                        )}
                    </Show>
                </VStack>
            </VStack>
        </Card>
    )
}
