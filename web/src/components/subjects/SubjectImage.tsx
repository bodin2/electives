import { Show } from 'solid-js'
import SubjectImagePlaceholder from '../../images/subject-image-placeholder.webp'
import { useI18n } from '../../providers/I18nProvider'

interface SubjectImageProps {
    imageUrl?: string
    class?: string
    placeholderClass?: string
}

export default function SubjectImage(props: SubjectImageProps) {
    const { string } = useI18n()

    return (
        <Show
            when={props.imageUrl}
            fallback={
                <img
                    src={SubjectImagePlaceholder}
                    class={props.placeholderClass}
                    alt={string.IMG_ALT_SUBJECT_IMAGE()}
                />
            }
        >
            <img src={props.imageUrl} class={props.class} alt={string.IMG_ALT_SUBJECT_IMAGE()} />
        </Show>
    )
}
