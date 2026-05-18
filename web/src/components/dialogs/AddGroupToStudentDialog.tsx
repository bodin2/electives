import AccountGroupIcon from '@iconify-icons/mdi/account-group-outline'
import { useI18n } from '~/providers/I18nProvider'
import { SelectGroupDialog } from './SelectGroupDialog'
import type { Group } from '~/api'

export default function AddGroupToStudentDialog(props: {
    open: boolean
    onClose: (picked: Group | null) => unknown
    groups: Group[]
    currentGroupIds: number[]
    headline?: string
    selectLabel?: string
    selectPlaceholder?: string
    /** When provided, a Reset button will be shown */
    onClear?: () => unknown
    initialId?: number
}) {
    const { string } = useI18n()

    return (
        <SelectGroupDialog
            open={props.open}
            value={props.initialId ?? null}
            onClose={() => props.onClose(null)}
            onSave={async id => {
                if (id === null) {
                    await props.onClear?.()
                    props.onClose(null)
                } else {
                    const group = props.groups.find(g => g.id === id) ?? null
                    props.onClose(group)
                }
            }}
            groups={props.groups.filter(g => !props.currentGroupIds.includes(g.id))}
            headline={props.headline ? <h1 class="m3-headline-small">{props.headline}</h1> : undefined}
            description={null}
            showReset={!!props.onClear}
            confirmLabel={string.ADD()}
            selectLabel={props.selectLabel}
            selectPlaceholder={props.selectPlaceholder}
            icon={AccountGroupIcon}
        />
    )
}
