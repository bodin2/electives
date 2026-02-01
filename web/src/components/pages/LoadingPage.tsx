import { LoadingIndicator } from 'm3-solid'
import { useI18n } from '../../providers/I18nProvider'
import { VStack } from '../Stack'

export default function LoadingPage() {
    const { string } = useI18n()

    return (
        <VStack alignHorizontal="center" alignVertical="center" grow>
            <div>
                <LoadingIndicator container />
            </div>
            <p class="m3-body-large" style={{ color: 'var(--m3c-on-surface)' }}>
                {string.LOADING()}
            </p>
        </VStack>
    )
}
