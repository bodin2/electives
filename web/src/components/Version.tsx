import { createSignal } from 'solid-js'
import { useI18n } from '../providers/I18nProvider'

const VERSION = `v${process.env.APP_VERSION}-${process.env.APP_COMMIT}`

export default function Version() {
    const { string } = useI18n()
    const [copied, setCopied] = createSignal(false)

    const copyVersion = () => {
        navigator.clipboard.writeText(VERSION)
        setCopied(true)
        setTimeout(() => setCopied(false), 3000)
    }

    return (
        <p class="credits" onClick={copyVersion} onKeyPress={copyVersion}>
            {copied() ? string.COPIED_TO_CLIPBOARD() : VERSION}
        </p>
    )
}
