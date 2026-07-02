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

interface EnrollmentProgress {
    selected: number
    total: number
}

interface AdminEnrollmentCardProps {
    enrollment: Enrollment
    class?: string
    hideDates?: boolean
    progress?: EnrollmentProgress
    onClick: (id: number) => void
}

const PROGRESS_SIZE = 72
const PROGRESS_THICKNESS = 6

function EnrollmentProgressIndicator(props: { progress: EnrollmentProgress }) {
    const { string } = useI18n()

    const percent = () => {
        const { selected, total } = props.progress
        if (total <= 0) return 0
        return Math.min(100, Math.max(0, (selected / total) * 100))
    }

    const r = PROGRESS_SIZE / 2 - PROGRESS_THICKNESS / 2
    const circumference = Math.PI * r * 2
    const dashoffset = () => circumference - (percent() / 100) * circumference

    return (
        <div class={styles.progressContainer} style={{ width: `${PROGRESS_SIZE}px`, height: `${PROGRESS_SIZE}px` }}>
            <svg
                width={PROGRESS_SIZE}
                height={PROGRESS_SIZE}
                viewBox={`0 0 ${PROGRESS_SIZE} ${PROGRESS_SIZE}`}
                class={styles.progressSvg}
                role="progressbar"
                aria-valuenow={percent()}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={string.ENROLLMENT_PROGRESS_ARIA({
                    percent: Math.round(percent()),
                    selected: props.progress.selected,
                    total: props.progress.total,
                })}
            >
                <circle
                    cx={PROGRESS_SIZE / 2}
                    cy={PROGRESS_SIZE / 2}
                    r={r}
                    stroke-width={PROGRESS_THICKNESS}
                    fill="none"
                    class={styles.progressTrack}
                />
                <circle
                    cx={PROGRESS_SIZE / 2}
                    cy={PROGRESS_SIZE / 2}
                    r={r}
                    stroke-width={PROGRESS_THICKNESS}
                    stroke-dasharray={`${circumference} ${circumference}`}
                    stroke-dashoffset={dashoffset()}
                    stroke-linecap="round"
                    fill="none"
                    class={styles.progressIndicator}
                />
            </svg>
            <div class={styles.progressLabel}>
                <span class="m3-body-large">{Math.round(percent())}%</span>
                <small class="m3-label-small">
                    {props.progress.selected}/{props.progress.total}
                </small>
            </div>
        </div>
    )
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
            <HStack gap={12} alignVertical="center" grow>
                <VStack gap={8} grow>
                    <h1 class="m3-title-large text-balance">{props.enrollment.name}</h1>

                    <VStack gap={4} style={{ color: 'var(--m3c-on-surface-variant)' }}>
                        <Show when={!props.hideDates}>
                            <HStack alignVertical="center" gap={4}>
                                <IconLabel
                                    icon={CalendarIcon}
                                    text={startDateStr()}
                                    iconSize={16}
                                    class="m3-body-medium"
                                />
                                <IconLabel
                                    icon={ArrowRightIcon}
                                    text={endDateStr()}
                                    iconSize={16}
                                    class="m3-body-medium"
                                />
                            </HStack>
                        </Show>

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

                <Show when={props.progress}>{progress => <EnrollmentProgressIndicator progress={progress()} />}</Show>
            </HStack>
        </Card>
    )
}
