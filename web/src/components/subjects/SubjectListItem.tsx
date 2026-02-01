import { User } from '../../api'
import SubjectThumbnailPlaceholder from '../../images/subject-thumbnail-placeholder.webp'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import LinkListItem from '../LinkListItem'
import type { Subject } from '../../api'

interface SubjectListItemProps {
    subject: Subject
    electiveId: number
    thumbnailClass?: string
}

export default function SubjectListItem(props: SubjectListItemProps) {
    const { string } = useI18n()
    const enrollment = useEnrollmentCounts()

    const enrolledCount = () => enrollment.getCount(props.electiveId, props.subject.id) ?? 0

    const isNearCapacity = () =>
        enrolledCount() / props.subject.capacity > 0.8 || props.subject.capacity - enrolledCount() < 5

    const teacherNames = () => props.subject.teachers.map(t => new User(t).fullName).join(', ') || '-'

    return (
        <LinkListItem
            lines={4}
            headline={props.subject.name}
            to={'/enroll/$electiveId/$subjectId'}
            preloadDelay={500}
            params={{
                electiveId: props.electiveId,
                subjectId: props.subject.id,
            }}
            leading={
                // @once
                <img
                    class={props.thumbnailClass}
                    src={props.subject.thumbnailUrl || SubjectThumbnailPlaceholder}
                    alt={string.IMG_ALT_SUBJECT_IMAGE()}
                />
            }
            trailing={
                // @once
                <p
                    class="m3-body-medium"
                    classList={{
                        'text-error': isNearCapacity(),
                    }}
                >
                    {string.MEMBERS_COUNT({ count: enrolledCount(), total: props.subject.capacity })}
                </p>
            }
            supporting={
                // @once
                <>
                    <p>{`${string.CLASS()}: ${props.subject.location}`}</p>
                    <p>{`${string.TEACHER()}: ${teacherNames()}`}</p>
                </>
            }
        />
    )
}
