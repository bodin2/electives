import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import BookIcon from '@iconify-icons/mdi/book-outline'
import LocationIcon from '@iconify-icons/mdi/location-on-outline'
import PencilIcon from '@iconify-icons/mdi/pencil'
import TeachIcon from '@iconify-icons/mdi/teach'
import { useNavigate } from '@tanstack/solid-router'
import { Card, Icon, mergeClasses } from 'm3-solid'
import { createSignal, Show } from 'solid-js'
import { User } from '../../api'
import useElectiveOpen from '../../hooks/useElectiveOpen'
import SubjectThumbnailPlaceholder from '../../images/subject-thumbnail-placeholder.webp'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { formatCountdown } from '../../utils/date'
import LinkButton from '../LinkButton'
import { HStack, VStack } from '../Stack'
import IconLabel from '../subjects/IconLabel'
import styles from './ElectiveCard.module.css'
import type { Elective, Subject } from '../../api'

interface ElectiveCardProps {
    elective: Elective
    class?: string
}

export default function ElectiveCard(props: ElectiveCardProps) {
    const { string, locale } = useI18n()
    const navigate = useNavigate()
    const api = useAPI()
    const [countdown, setCountdown] = createSignal<number | null>(null)
    const isOpen = useElectiveOpen(props.elective, {
        onCountdown: timeRemaining => setCountdown(timeRemaining),
    })

    const user = () => api.client.user
    const selectedSubject = (): Subject | undefined => {
        if (!user()?.isStudent()) return undefined
        const currentUser = user()
        if (!currentUser) return undefined
        return api.client.selections.resolveSelection(currentUser.id, props.elective.id)
    }

    const hasSelection = () => selectedSubject() !== undefined

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

    const teacherNames = () => {
        const subject = selectedSubject()
        if (!subject) return ''
        return subject.teachers.map(t => new User(t).fullName).join(', ')
    }

    return (
        <Card
            variant={hasSelection() ? 'outlined' : isOpen() ? 'elevated' : 'outlined'}
            data-open={isOpen()}
            data-has-selection={hasSelection()}
            class={mergeClasses(props.class, styles.card)}
            onClick={
                hasSelection()
                    ? undefined
                    : () => {
                          navigate({
                              to: '/enroll/$electiveId',
                              params: { electiveId: props.elective.id },
                          })
                      }
            }
        >
            <Show
                when={hasSelection()}
                fallback={
                    <HStack alignHorizontal="space-between" alignVertical="center" gap={16} grow>
                        <VStack gap={4}>
                            <h1 class="m3-title-large">{props.elective.name}</h1>
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
                    <HStack alignHorizontal="space-between">
                        <VStack>
                            <p class="m3-title-large">{props.elective.name}</p>
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
                                    class="m3-body-small"
                                />
                                <IconLabel
                                    icon={TeachIcon}
                                    text={`${string.TEACHER()} (${selectedSubject()?.teachers.length}): ${teacherNames()}`}
                                    iconSize={16}
                                    class="m3-body-small"
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
                            to="/enroll/$electiveId"
                            params={{ electiveId: props.elective.id }}
                        >
                            {string.ENROLLMENT_EDIT()}
                        </LinkButton>
                    </Show>
                </VStack>
            </Show>
        </Card>
    )
}
