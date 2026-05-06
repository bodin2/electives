import SaveIcon from '@iconify-icons/mdi/content-save'
import { type ParentProps, Show } from 'solid-js'
import { SubjectTag } from '../../api'
import { useI18n } from '../../providers/I18nProvider'
import BottomBar, { bottomBarStyles } from '../BottomBar'
import { Button } from '../Button'
import { useSubjectInfoContext } from './SubjectInfo'

export default function SubjectBottomActions(props: ParentProps) {
    const ctx = useSubjectInfoContext()
    const { string } = useI18n()

    const isValid = () => {
        return ctx.subject.tag !== SubjectTag.UNRECOGNIZED
    }

    return (
        <BottomBar>
            <Show when={props.children}>
                <div class={bottomBarStyles.item}>{props.children}</div>
            </Show>
            <Show when={ctx.onSave && ctx.editable}>
                <Button
                    icon={SaveIcon}
                    class={bottomBarStyles.item}
                    size="m"
                    onClick={() => ctx.onSave?.()}
                    disabled={!isValid()}
                >
                    {string.SAVE()}
                </Button>
            </Show>
        </BottomBar>
    )
}
