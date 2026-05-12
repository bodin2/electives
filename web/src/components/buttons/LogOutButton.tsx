import LogOutIcon from '@iconify-icons/mdi/logout'
import { splitProps } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import type { ButtonProps } from 'm3-solid/src'
import type { JSX } from 'solid-js/h/jsx-runtime'

export default function LogOutButton(
    props: Partial<Extract<ButtonProps, JSX.HTMLAttributes<HTMLButtonElement>>> & {
        noText?: boolean
    },
) {
    const [local, others] = splitProps(props, ['noText'])
    const api = useAPI()
    const { string } = useI18n()

    return (
        <Button
            {...others}
            icon={LogOutIcon}
            variant="tonal-error"
            onClick={() => api.logout()}
            aria-label={string.LOGOUT()}
        >
            {/* Don't use <Show> here, so the button can detect it has "no" elements */}
            {!local.noText && string.LOGOUT()}
        </Button>
    )
}
