import { Show } from 'solid-js'
import SubjectImagePlaceholder from '../../images/subject-image-placeholder.webp'
import { useI18n } from '../../providers/I18nProvider'
import styles from './SubjectImage.module.css'

interface SubjectImageProps {
    imageUrl?: string
    /** Optional override class. */
    class?: string
}

export default function SubjectImage(props: SubjectImageProps) {
    const { string } = useI18n()

    return (
        <Show
            when={props.imageUrl}
            fallback={
                <img
                    src={SubjectImagePlaceholder}
                    class={props.class ?? styles.placeholder}
                    alt={string.IMG_ALT_SUBJECT_IMAGE()}
                />
            }
        >
            <img src={props.imageUrl} class={props.class ?? styles.image} alt={string.IMG_ALT_SUBJECT_IMAGE()} />
        </Show>
    )
}
