import DeleteOutlineIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { Show } from 'solid-js'
import { User } from '../../api'
import SubjectThumbnailPlaceholder from '../../images/subject-thumbnail-placeholder.webp'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { Button } from '../Button'
import LinkListItem from '../LinkListItem'
import { HStack, VStack } from '../Stack'
import { useSubjectDisplayContext } from './SubjectDisplayContext'
import styles from './SubjectListItem.module.css'
import type { Subject } from '../../api'

interface SubjectListItemProps {
    subject: Subject
}

export default function SubjectListItem(props: SubjectListItemProps) {
    const { string } = useI18n()
    const enrollment = useEnrollmentCounts()
    const ctx = useSubjectDisplayContext()

    const electiveId = () => ctx.elective?.id

    const enrolledCount = () => {
        const id = electiveId()
        return id !== undefined ? (enrollment.getCount(id, props.subject.id) ?? 0) : 0
    }

    const isNearCapacity = () => {
        if (electiveId() === undefined) return false
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
            <Show when={ctx.editable}>
                <HStack gap={8}>
                    <Button
                        variant="text"
                        iconType="only"
                        icon={PencilOutlineIcon}
                        aria-label={string.EDIT_SUBJECT()}
                    />
                    <Button
                        variant="tonal-error"
                        iconType="only"
                        icon={DeleteOutlineIcon}
                        aria-label={string.DELETE_SUBJECT()}
                        onClick={e => {
                            e.stopPropagation()
                            ctx.setDeletingSubject(props.subject)
                        }}
                    />
                </HStack>
            </Show>
            <Show when={electiveId() !== undefined}>
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

    const linkProps = () =>
        ctx.editable
            ? ctx.editLinkProps(props.subject.id)
            : electiveId() !== undefined
              ? ctx.viewLinkProps(nonNull(electiveId()), props.subject.id)
              : {}

    return (
        <LinkListItem
            {...linkProps()}
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
