import { createQuery, skipToken } from '@tanstack/solid-query'
import { ListItem, mergeClasses } from 'm3-solid/src'
import { createMemo, type JSX, Show, Suspense } from 'solid-js'
import SubjectThumbnailPlaceholder from '../../images/subject-thumbnail-placeholder.webp'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { groupQueryOptions } from '../../queries/groups'
import { GroupBadge } from '../Badges'
import LinkListItem from '../LinkListItem'
import { HStack, VStack } from '../Stack'
import styles from './SubjectListItem.module.css'
import type { LinkProps } from '@tanstack/solid-router'
import type { Subject } from '../../api'

interface SubjectListItemProps {
    subject: Subject
    editable?: boolean
    enrollmentId?: number
    actions?: JSX.Element
    linkProps?: LinkProps
    onClick?: () => void
    selected?: boolean
}

export default function SubjectListItem(props: SubjectListItemProps) {
    const { string } = useI18n()
    const api = useAPI()
    const enrollment = useEnrollmentCounts()

    const groupQuery = createQuery(() => ({
        ...groupQueryOptions(api.client, props.subject.groupId ?? skipToken),
        enabled: api.client.user?.isAdmin() ?? props.subject.groupId !== undefined,
    }))

    const enrolledCount = () => {
        return props.enrollmentId !== undefined ? (enrollment.getCount(props.enrollmentId, props.subject.id) ?? 0) : 0
    }

    const isNearCapacity = () => {
        if (props.enrollmentId === undefined) return false
        return enrolledCount() / props.subject.capacity > 0.8 || props.subject.capacity - enrolledCount() < 5
    }

    const teacherNames = () => {
        if (props.enrollmentId === undefined) return null
        const teachers = api.client.subjects.resolveTeachers(props.enrollmentId, props.subject.id)
        return (teachers?.map(t => t.displayName) ?? []).join(', ') || null
    }

    const Leading = (
        <img
            class={styles.thumbnail}
            src={props.subject.thumbnailUrl || SubjectThumbnailPlaceholder}
            alt={string.IMG_ALT_SUBJECT_IMAGE()}
        />
    )

    const Trailing = (
        <VStack alignHorizontal="end">
            <Show when={props.actions}>{props.actions}</Show>
            <Show when={props.enrollmentId !== undefined}>
                <p
                    class="m3-body-medium"
                    classList={{
                        'text-error': isNearCapacity(),
                    }}
                >
                    {string.MEMBERS_COUNT({ count: enrolledCount(), total: props.subject.capacity })}
                </p>
            </Show>
        </VStack>
    )

    const supporting = (
        <>
            <p>{`${props.subject.code} • ${string.CLASS()}: ${props.subject.location}`}</p>
            <p>{''}</p>
            <Show when={teacherNames()}>{names => <p>{`${string.TEACHERS()}: ${names()}`}</p>}</Show>
        </>
    )

    const commonProps = createMemo(() => ({
        class: mergeClasses(styles.item, props.selected && styles.selected),
        lines: 4 as const,
        headline: (
            /* @once */
            <HStack alignVertical="center">
                {props.subject.name}
                <Suspense>
                    <Show when={groupQuery.data}>{group => <GroupBadge group={group()} />}</Show>
                </Suspense>
            </HStack>
        ),
        leading: Leading,
        trailing: Trailing,
        supporting,
    }))

    const ariaLabel = () =>
        [
            `${props.subject.name} (${props.subject.code})`,
            `${string.CLASS()}: ${props.subject.location}`,
            teacherNames() && `${string.TEACHERS()}: ${teacherNames()}`,
        ]
            .filter(Boolean)
            .join(', ')

    return (
        <Show
            when={props.onClick}
            fallback={
                <LinkListItem aria-label={ariaLabel()} {...props.linkProps} {...commonProps()} preloadDelay={500} />
            }
        >
            {onClick => <ListItem aria-label={ariaLabel()} onClick={onClick()} {...commonProps()} />}
        </Show>
    )
}
