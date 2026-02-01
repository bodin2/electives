import AccountCircleIcon from '@iconify-icons/mdi/account-circle-outline'
import HashTagBoxOutlineIcon from '@iconify-icons/mdi/hashtag-box-outline'
import LocationIcon from '@iconify-icons/mdi/location-on-outline'
import TeachIcon from '@iconify-icons/mdi/teach'
import { User } from '../../api'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { HStack, VStack } from '../Stack'
import IconLabel from './IconLabel'
import SubjectImage from './SubjectImage'
import type { Subject } from '../../api'

interface SubjectDetailsTabProps {
    subject: Subject
    electiveId: number
    imageClass?: string
    imagePlaceholderClass?: string
    descriptionClass?: string
    labelClass?: string
}

export default function SubjectDetailsTab(props: SubjectDetailsTabProps) {
    const { string } = useI18n()
    const enrollment = useEnrollmentCounts().getElectiveCounts(props.electiveId)
    const enrolledCount = () => enrollment[props.subject.id] ?? 0

    const teachersText = () => {
        const teachers = props.subject.teachers.map(t => new User(t).fullName).join(', ') || '-'
        return `${string.TEACHER()} (${props.subject.teachers.length}): ${teachers}`
    }

    return (
        <>
            <SubjectImage
                imageUrl={props.subject.imageUrl}
                class={props.imageClass}
                placeholderClass={props.imagePlaceholderClass}
            />
            <VStack gap={4}>
                <h1 class="m3-headline-medium">{props.subject.name}</h1>
                <VStack gap={0} style={{ color: 'var(--m3c-on-surface-variant)' }}>
                    <HStack gap={16} wrap>
                        <IconLabel icon={HashTagBoxOutlineIcon} text={props.subject.code} class={props.labelClass} />
                        <IconLabel icon={LocationIcon} text={props.subject.location} class={props.labelClass} />
                        <IconLabel
                            icon={AccountCircleIcon}
                            text={string.SUBJECT_MEMBERS_COUNT({
                                count: enrolledCount(),
                                total: props.subject.capacity,
                            })}
                            class={props.labelClass}
                        />
                    </HStack>
                    <IconLabel icon={TeachIcon} text={teachersText()} class={props.labelClass} />
                </VStack>
            </VStack>
            <p class={props.descriptionClass}>{props.subject.description}</p>
        </>
    )
}
