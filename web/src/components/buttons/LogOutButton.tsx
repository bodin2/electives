import LogOutIcon from '@iconify-icons/mdi/logout'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import type { ButtonProps } from 'm3-solid'
import type { JSX } from 'solid-js/h/jsx-runtime'

export default function LogOutButton(props: Partial<Extract<ButtonProps, JSX.HTMLAttributes<HTMLButtonElement>>>) {
    const api = useAPI()
    const { string } = useI18n()

    return (
        <Button {...props} icon={LogOutIcon} variant="tonal-error" onClick={() => api.logout()}>
            {string.LOGOUT()}
        </Button>
    )
}
