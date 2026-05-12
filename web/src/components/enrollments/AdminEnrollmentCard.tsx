import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import CalendarIcon from '@iconify-icons/mdi/calendar-clock-outline'
import PeopleIcon from '@iconify-icons/mdi/people-outline'
import { createQuery } from '@tanstack/solid-query'
import { Card, mergeClasses } from 'm3-solid/src'
import { Show } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { groupsQueryOptions } from '~/queries/groups'
import { formatDuration } from '~/utils/date'
import IconLabel from '../IconLabel'
import { HStack, VStack } from '../Stack'
import styles from './AdminEnrollmentCard.module.css'
import type { Enrollment } from '~/api'

interface AdminEnrollmentCardProps {
    enrollment: Enrollment
    class?: string
    onClick: (id: number) => void
}

export default function AdminEnrollmentCard(props: AdminEnrollmentCardProps) {
    const { string, locale } = useI18n()
    const { client } = useAPI()

    const groupsQuery = createQuery(() => groupsQueryOptions(client))

    const startDateStr = () =>
        props.enrollment.startDate ? formatDuration(locale, props.enrollment.startDate) : string.NOT_SET()
    const endDateStr = () =>
        props.enrollment.endDate ? formatDuration(locale, props.enrollment.endDate) : string.NOT_SET()

    const groupName = () => {
        if (props.enrollment.groupId === null || !groupsQuery.data) return undefined
        return groupsQuery.data.find(g => g.id === props.enrollment.groupId)?.name
    }

    return (
        <Card
            variant="outlined"
            data-open={props.enrollment.isSelectionOpen()}
            class={mergeClasses(props.class, styles.card)}
            onClick={() => props.onClick(props.enrollment.id)}
        >
            <VStack gap={8} grow>
                <h1 class="m3-title-large text-balance">{props.enrollment.name}</h1>

                <VStack gap={4} style={{ color: 'var(--m3c-on-surface-variant)' }}>
                    <HStack alignVertical="center" gap={4}>
                        <IconLabel icon={CalendarIcon} text={startDateStr()} iconSize={16} class="m3-body-medium" />
                        <IconLabel icon={ArrowRightIcon} text={endDateStr()} iconSize={16} class="m3-body-medium" />
                    </HStack>

                    <Show when={groupName()}>
                        {name => (
                            <IconLabel
                                icon={PeopleIcon}
                                text={`${string.GROUP()}: ${name()}`}
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
