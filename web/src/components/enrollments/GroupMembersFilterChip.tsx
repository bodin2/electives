import DropDownIcon from '@iconify-icons/mdi/arrow-drop-down'
import { createQuery } from '@tanstack/solid-query'
import { Chip, Icon, MenuItem } from 'm3-solid/src'
import { createMemo, createSignal, For } from 'solid-js'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentsQueryOptions } from '~/queries/enrollments'
import { AnchoredMenu } from '../AnchoredMenu'

interface GroupMembersFilterChipProps {
    groupId: number
    value: number | null
    onChange: (enrollmentId: number | null) => void
}

export function GroupMembersFilterChip(props: GroupMembersFilterChipProps) {
    const { client } = useAPI()
    const { string } = useI18n()
    const [open, setOpen] = createSignal(false)
    const [anchor, setAnchor] = createSignal<HTMLButtonElement | undefined>()

    const enrollmentsQuery = createQuery(() => ({
        ...enrollmentsQueryOptions(client),
        notifyOnChangeProps: ['data'],
    }))

    // No restrictions or this group restricted
    const applicable = createMemo(() =>
        (enrollmentsQuery.data ?? []).filter(e => e.groupId === null || e.groupId === props.groupId),
    )

    const currentLabel = () => {
        if (props.value === null) return string.ALL_STUDENTS()
        const e = applicable().find(en => en.id === props.value)
        return e ? string.UNENROLLED_IN_X({ name: e.name }) : string.ALL_STUDENTS()
    }

    const select = (id: number | null) => {
        props.onChange(id)
        setOpen(false)
    }

    return (
        <>
            <Chip
                ref={setAnchor}
                variant="input"
                selected
                trailing={
                    <Icon
                        size={18}
                        icon={DropDownIcon}
                        style={{ transition: 'rotate var(--m3-easing-slow)', rotate: open() ? '180deg' : '0deg' }}
                    />
                }
                onClick={() => setOpen(o => !o)}
                aria-haspopup="menu"
                aria-expanded={open()}
            >
                {currentLabel()}
            </Chip>
            <AnchoredMenu
                anchor={anchor()}
                open={open()}
                onClose={() => setOpen(false)}
                placement="bottom-start"
                matchAnchorWidth
            >
                <MenuItem role="menuitem" onClick={() => select(null)}>
                    {string.ALL_STUDENTS()}
                </MenuItem>
                <For each={applicable()}>
                    {enrollment => (
                        <MenuItem role="menuitem" onClick={() => select(enrollment.id)}>
                            {string.UNENROLLED_IN_X({ name: enrollment.name })}
                        </MenuItem>
                    )}
                </For>
            </AnchoredMenu>
        </>
    )
}
