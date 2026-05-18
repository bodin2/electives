import SaveIcon from '@iconify-icons/mdi/content-save'
import { Show } from 'solid-js'
import { useI18n } from '~/providers/I18nProvider'
import BottomBar, { bottomBarStyles } from '../BottomBar'
import { Button } from '../Button'
import { useUserDisplayContext } from './UserDisplayContext'

export default function UserBottomActions() {
    const ctx = useUserDisplayContext()
    const { string } = useI18n()

    return (
        <BottomBar>
            <Show when={ctx.editable}>
                <Button
                    disabled={!ctx.creating && !ctx.edited}
                    onClick={async e => {
                        const form = (e.target as HTMLButtonElement).form
                        if (!form) return

                        form.dispatchEvent(new Event('trysubmit'))

                        if (form.reportValidity()) {
                            await ctx.onSave?.()
                        }
                    }}
                    type="submit"
                    icon={SaveIcon}
                    class={bottomBarStyles.item}
                    size="m"
                    form="user-details"
                >
                    {string.SAVE()}
                </Button>
            </Show>
        </BottomBar>
    )
}
