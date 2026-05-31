import { ListItem, Switch } from 'm3-solid/src'
import { useI18n } from '~/providers/I18nProvider'
import { Dialog } from '../Dialog'

export default function SettingsDialog(props: { open: boolean; onClose: () => void }) {
    const { string, setLocale, locale } = useI18n()

    return (
        <Dialog headline={string.SETTINGS()} onClose={props.onClose} open={props.open} actions={null} closedBy="any">
            <ListItem
                style={{ width: '100%' }}
                headline={string.SETTING_THAI()}
                supporting={string.SETTING_THAI_DESCRIPTION()}
                onClick={() => setLocale(locale() === 'th' ? 'en' : 'th')}
                trailing={<Switch checked={locale() === 'th'} />}
            />
        </Dialog>
    )
}
