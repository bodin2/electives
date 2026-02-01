import { useI18n } from '../../providers/I18nProvider'
import { Dialog } from '../Dialog'

// TODO
export default function SettingsDialog(props: { open: boolean; onClose: () => void }) {
    const { string } = useI18n()
    return (
        <Dialog headline={string.SETTINGS()} onClose={props.onClose} open={props.open} actions={null} closedBy="any">
            Settings
        </Dialog>
    )
}
