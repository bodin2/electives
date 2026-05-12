import { mergeClasses } from 'm3-solid/src'
import { Show } from 'solid-js'
import AvatarPlaceholder from '../../images/avatar-placeholder.webp'
import { useI18n } from '../../providers/I18nProvider'

interface UserAvatarProps {
    imageUrl?: string
    class?: string
    placeholderClass?: string
}

export default function UserAvatar(props: UserAvatarProps) {
    const { string } = useI18n()

    return (
        <Show
            when={props.imageUrl}
            fallback={
                <img
                    src={AvatarPlaceholder}
                    class={mergeClasses(props.placeholderClass, props.class)}
                    alt={string.AVATAR()}
                />
            }
        >
            <img src={props.imageUrl} class={props.class} alt={string.AVATAR()} />
        </Show>
    )
}
