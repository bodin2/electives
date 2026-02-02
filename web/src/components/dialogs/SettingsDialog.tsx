import { ListItem, Switch } from 'm3-solid'
import { createEffect, createSignal } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { Dialog } from '../Dialog'

export default function SettingsDialog(props: { open: boolean; onClose: () => void }) {
    const { string, setLocale, locale } = useI18n()
    const [useThai, setUseThai] = createSignal(locale() === 'th')

    createEffect(() => {
        setLocale(useThai() ? 'th' : 'en')
    })

    return (
        <Dialog headline={string.SETTINGS()} onClose={props.onClose} open={props.open} actions={null} closedBy="any">
            <ListItem
                style={{ width: '100%' }}
                headline={string.SETTING_THAI()}
                supporting={string.SETTING_THAI_DESCRIPTION()}
                onClick={() => setUseThai(!useThai())}
                trailing={
                    <Switch
                        checked={useThai()}
                        onClick={e => e.stopPropagation()}
                        onChange={() => setUseThai(!useThai())}
                    />
                }
            />
        </Dialog>
    )
}
