import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import BookIcon from '@iconify-icons/mdi/book-outline'
import LocationIcon from '@iconify-icons/mdi/location-on-outline'
import PencilIcon from '@iconify-icons/mdi/pencil'
import TeachIcon from '@iconify-icons/mdi/teach'
import { Card, Icon, mergeClasses } from 'm3-solid/src'
import { createSignal, Show } from 'solid-js'
import useEnrollmentOpen from '~/hooks/useEnrollmentOpen'
import SubjectThumbnailPlaceholder from '~/images/subject-thumbnail-placeholder.webp'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { formatCountdown, formatDuration } from '~/utils/date'
import IconLabel from '../IconLabel'
import LinkButton from '../LinkButton'
import { HStack, VStack } from '../Stack'
import styles from './EnrollmentCard.module.css'
import type { Enrollment, Subject } from '~/api'

interface EnrollmentCardProps {
    enrollment: Enrollment
    class?: string
    onClick: (id: number) => void
}

export default function EnrollmentCard(props: EnrollmentCardProps) {
    const { string, locale } = useI18n()
    const api = useAPI()
    const [countdown, setCountdown] = createSignal<number | null>(null)
    const isOpen = useEnrollmentOpen(props.enrollment, {
        onCountdown: timeRemaining => setCountdown(timeRemaining),
    })

    const user = () => api.client.user
    const selectedSubject = (): Subject | undefined => {
        if (!user()?.isStudent()) return undefined
        const currentUser = user()
        if (!currentUser) return undefined
        return api.client.selections.resolveSelection(currentUser.id, props.enrollment.id)
    }

    const hasSelection = () => selectedSubject() !== undefined

    const formatDateRange = () => {
        const { startDate, endDate } = props.enrollment
        if (isOpen()) {
            if (endDate) return string.ENROLLMENT_CLOSES_AT({ duration: formatDuration(locale, endDate) })
        } else {
            const countdownText = formatCountdown(countdown())
            if (countdownText) return string.ENROLLMENT_CLOSED_OPENING_IN({ time: countdownText })
            if (startDate) {
                if (startDate.getTime() < Date.now()) return null

                return string.ENROLLMENT_OPENS_AT({ duration: formatDuration(locale, startDate) })
            }
        }
        return null
    }

    const teacherNames = () => {
        const subject = selectedSubject()
        if (!subject) return ''
        const teachers = api.client.subjects.resolveTeachers(props.enrollment.id, subject.id)
        return (teachers?.map(t => t.displayName) ?? []).join(', ')
    }

    return (
        <Card
            variant={hasSelection() ? 'outlined' : isOpen() ? 'elevated' : 'outlined'}
            data-open={isOpen()}
            data-has-selection={hasSelection()}
            class={mergeClasses(props.class, styles.card)}
            onClick={hasSelection() ? undefined : () => props.onClick(props.enrollment.id)}
        >
            <Show
                when={hasSelection()}
                fallback={
                    <HStack alignHorizontal="space-between" alignVertical="center" gap={16} grow>
                        <VStack gap={4}>
                            <h1 class="m3-title-large text-balance">{props.enrollment.name}</h1>
                            <VStack gap={2}>
                                <p class="m3-body-medium">
                                    {isOpen() ? string.ENROLLMENT_OPEN() : string.ENROLLMENT_CLOSED()}
                                </p>
                                <Show when={formatDateRange()}>
                                    <p class="m3-body-small">{formatDateRange()}</p>
                                </Show>
                            </VStack>
                        </VStack>
                        <Show when={isOpen()}>
                            <Icon width={24} height={24} icon={ArrowRightIcon} />
                        </Show>
                    </HStack>
                }
            >
                <VStack gap={16} alignVertical="space-between" grow>
                    <HStack alignHorizontal="space-between" class={styles.subjectInfo} gap={16}>
                        <VStack>
                            <p class="m3-title-large text-balance">{props.enrollment.name}</p>
                            <VStack gap={2} style={{ 'margin-top': '4px', color: 'var(--m3c-on-surface-variant)' }}>
                                <IconLabel
                                    icon={BookIcon}
                                    text={`${selectedSubject()?.name} (${selectedSubject()?.code})`}
                                    iconSize={16}
                                    class="m3-body-medium"
                                />
                                <IconLabel
                                    icon={LocationIcon}
                                    text={`${selectedSubject()?.location}`}
                                    iconSize={16}
                                    class="m3-body-medium"
                                />
                                <IconLabel
                                    icon={TeachIcon}
                                    text={`${string.TEACHERS()}: ${teacherNames()}`}
                                    iconSize={16}
                                    class="m3-body-medium"
                                />
                            </VStack>
                        </VStack>
                        <img
                            src={selectedSubject()?.thumbnailUrl || SubjectThumbnailPlaceholder}
                            class={styles.thumbnail}
                            alt={string.IMG_ALT_SUBJECT_IMAGE()}
                        />
                    </HStack>
                    <Show when={isOpen()}>
                        <LinkButton
                            icon={PencilIcon}
                            variant="tonal"
                            to="/enroll/$enrollmentId"
                            params={{ enrollmentId: props.enrollment.id }}
                        >
                            {string.ENROLLMENT_EDIT()}
                        </LinkButton>
                    </Show>
                </VStack>
            </Show>
        </Card>
    )
}
