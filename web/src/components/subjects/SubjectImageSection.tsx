import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { Show } from 'solid-js'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../Button'
import { HStack } from '../Stack'
import styles from './SubjectDetailsTab.module.css'
import SubjectImage from './SubjectImage'
import { useSubjectInfoContext } from './SubjectInfo'

interface SubjectImageSectionProps {
    onStartEditing: (key: string) => void
}

export default function SubjectImageSection(props: SubjectImageSectionProps) {
    const { string } = useI18n()
    const ctx = useSubjectInfoContext()

    return (
        <div style={{ position: 'relative' }}>
            <SubjectImage imageUrl={ctx.subject.imageUrl} />
            <Show when={ctx.subject.thumbnailUrl}>
                <div style={{ position: 'absolute', bottom: '8px', left: '8px' }}>
                    <SubjectImage imageUrl={ctx.subject.thumbnailUrl} class={styles.thumbnail} />
                </div>
            </Show>
            <Show when={ctx.editable && ctx.onEdit}>
                <HStack style={{ position: 'absolute', top: '8px', right: '8px' }} gap={8}>
                    <Button
                        size="xs"
                        variant="tonal"
                        icon={PencilOutlineIcon}
                        onClick={() => props.onStartEditing('thumbnailUrl')}
                    >
                        {string.THUMBNAIL_URL()}
                    </Button>
                    <Button
                        size="xs"
                        variant="tonal"
                        icon={PencilOutlineIcon}
                        onClick={() => props.onStartEditing('imageUrl')}
                    >
                        {string.IMAGE_URL()}
                    </Button>
                </HStack>
            </Show>
        </div>
    )
}
