import NotFoundIcon from '../../images/not-found.webp'
import { useI18n } from '../../providers/I18nProvider'
import { VStack } from '../Stack'
import styles from './Image.module.css'
import type { ComponentProps } from 'solid-js'
import type { StyleRecordOnly } from '../../global'

export default function NotFoundIllustration(props: StyleRecordOnly<ComponentProps<'div'>>) {
    const { string } = useI18n()
    return (
        <VStack aria-hidden="true" {...props}>
            <img draggable="false" class={styles.image} src={NotFoundIcon} alt={string.IMG_ALT_NOT_FOUND()} />
        </VStack>
    )
}
