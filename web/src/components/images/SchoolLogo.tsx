import { mergeClasses } from 'm3-solid'
import SchoolLogoIcon from '../../images/school.webp'
import { useI18n } from '../../providers/I18nProvider'
import { VStack } from '../Stack'
import styles from './Image.module.css'
import type { ComponentProps } from 'solid-js'
import type { StyleRecordOnly } from '../../global'

export default function SchoolLogo(
    props: StyleRecordOnly<ComponentProps<'div'>> & { imageProps?: ComponentProps<'img'> },
) {
    const { string } = useI18n()
    return (
        <VStack aria-hidden="true" {...props}>
            <img
                draggable="false"
                src={SchoolLogoIcon}
                alt={string.IMG_ALT_SCHOOL_BRANDING()}
                {...props.imageProps}
                class={mergeClasses(styles.image, props.imageProps?.class)}
            />
        </VStack>
    )
}
