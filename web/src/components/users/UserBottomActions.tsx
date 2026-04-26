import SaveIcon from '@iconify-icons/mdi/content-save'
import { Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { useScrollData } from '../../providers/ScrollDataProvider'
import { Button } from '../Button'
import { VStack } from '../Stack'
import { useUserDisplayContext } from './UserDisplayContext'
import styles from './UserInfo.module.css'

export default function UserBottomActions() {
    const ctx = useUserDisplayContext()
    const { string } = useI18n()
    const scrollData = useScrollData()

    return (
        <VStack
            alignHorizontal="center"
            class={styles.actionButtonsContainer}
            style={{
                'border-top-color':
                    scrollData.maxScrollY - scrollData.scrollY > 16 ? 'var(--m3c-outline-variant)' : undefined,
            }}
        >
            <Show when={ctx.editable}>
                <Button
                    disabled={!ctx.creating && !ctx.edited}
                    onClick={async e => {
                        if ((e.target as HTMLButtonElement).form?.reportValidity()) {
                            await ctx.onSave?.()
                        }
                    }}
                    type="submit"
                    icon={SaveIcon}
                    class={styles.actionButton}
                    size="m"
                    form="user-details"
                >
                    {string.SAVE()}
                </Button>
            </Show>
        </VStack>
    )
}
