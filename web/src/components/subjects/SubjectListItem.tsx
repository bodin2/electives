import type { LinkProps } from '@tanstack/solid-router'
import { type JSX, Show } from 'solid-js'
import { User } from '../../api'
import SubjectThumbnailPlaceholder from '../../images/subject-thumbnail-placeholder.webp'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import LinkListItem from '../LinkListItem'
import { VStack } from '../Stack'
import styles from './SubjectListItem.module.css'
import type { Subject } from '../../api'

interface SubjectListItemProps {
    subject: Subject
    editable?: boolean
    electiveId?: number
    actions?: JSX.Element
    linkProps?: LinkProps
}

export default function SubjectListItem(props: SubjectListItemProps) {
    const { string } = useI18n()
    const enrollment = useEnrollmentCounts()

    const enrolledCount = () => {
        return props.electiveId !== undefined ? (enrollment.getCount(props.electiveId, props.subject.id) ?? 0) : 0
    }

    const isNearCapacity = () => {
        if (props.electiveId === undefined) return false
        return enrolledCount() / props.subject.capacity > 0.8 || props.subject.capacity - enrolledCount() < 5
    }

    const teacherNames = () => props.subject.teachers.map(t => new User(t).fullName).join(', ') || '-'

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
            <Show when={props.electiveId !== undefined}>
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
            <p>{`${string.CLASS()}: ${props.subject.location}`}</p>
            <p>{`${string.TEACHERS()}: ${teacherNames()}`}</p>
        </>
    )

    return (
        <LinkListItem
            {...(props.linkProps ?? {})}
            class={styles.item}
            lines={4}
            headline={props.subject.name}
            preloadDelay={500}
            leading={Leading}
            trailing={Trailing}
            supporting={supporting}
        />
    )
}
