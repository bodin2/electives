import MagnifyIcon from '@iconify-icons/mdi/magnify'
import PlusIcon from '@iconify-icons/mdi/plus'
import { TextField } from 'm3-solid/src'
import { createMemo, createSignal, For, type JSX, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { User } from '~/api'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../Button'
import { ConfirmDialog } from '../dialogs/base/ConfirmDialog'
import { HStack, VStack } from '../Stack'
import GroupItem from './GroupItem'
import styles from './GroupList.module.css'
import type { Group } from '~/api/structures'

interface GroupListProps {
    groups: Group[]
    memberCounts: Record<number, number>
    onEdit: (group: Group) => void
    onCreate: () => void
    onDelete: (group: Group) => Promise<void>
    emptyElement?: JSX.Element
}

export default function GroupList(props: GroupListProps) {
    const { string } = useI18n()
    const [search, setSearch] = createSignal('')
    // So the dialog can exit without changing the content
    const [deletingGroup, setDeletingGroup] = createSignal(false)
    let groupToDelete: Group | undefined

    const subGroupsByParent = createMemo(() => {
        const map: Record<number, Group[]> = {}
        for (const g of props.groups) {
            // biome-ignore lint/suspicious/noAssignInExpressions: This is fine
            if (g.isSub()) (map[g.parentId] ??= []).push(g)
        }
        // Sort each bucket
        for (const key in map) {
            map[key].sort(User.GROUP_SORTER)
        }
        return map
    })

    const filteredGroups = createMemo(() => {
        const query = search().toLowerCase()
        return props.groups
            .filter(
                g =>
                    g.isRoot() &&
                    (g.name.toLowerCase().includes(query) ||
                        (subGroupsByParent()[g.id]?.some(g => g.name.toLowerCase().includes(query)) ?? false)),
            )
            .sort(User.GROUP_SORTER)
    })

    const setGroupToDelete = (group: Group | undefined) => {
        groupToDelete = group
        setDeletingGroup(!!group)
    }

    return (
        <VStack class={styles.container} gap={0} grow>
            <HStack class={styles.searchContainer} alignVertical="center" gap={16} wrap>
                <TextField
                    leadingIcon={MagnifyIcon}
                    label={string.SEARCH_GROUPS()}
                    variant="filled"
                    class={styles.search}
                    placeholder={string.SEARCH_GROUPS()}
                    onInput={e => setSearch(e.target.value)}
                />
                <Button variant="filled" icon={PlusIcon} onClick={props.onCreate}>
                    {string.CREATE_GROUP()}
                </Button>
            </HStack>

            <Show when={props.groups.length > 0} fallback={props.emptyElement}>
                <VStack gap={0} class={styles.list}>
                    <For each={filteredGroups()}>
                        {group => (
                            <GroupItem
                                expanded={search() !== '' ? true : undefined}
                                group={group}
                                onEdit={props.onEdit}
                                onDelete={g => setGroupToDelete(g)}
                                memberCount={props.memberCounts[group.id] ?? 0}
                                memberCounts={props.memberCounts}
                                subGroups={subGroupsByParent()[group.id]}
                            />
                        )}
                    </For>
                </VStack>
            </Show>

            <Portal>
                <ConfirmDialog
                    open={deletingGroup()}
                    variant="danger"
                    closedBy="any"
                    onCancel={() => setGroupToDelete(undefined)}
                    onConfirm={async () => {
                        if (groupToDelete) await props.onDelete(groupToDelete)
                        setGroupToDelete(undefined)
                    }}
                    confirmText={string.DELETE_GROUP()}
                    headline={string.DELETE_GROUP()}
                >
                    <p>{string.CONFIRM_DELETE_GROUP({ name: <strong>{groupToDelete?.name ?? ''}</strong> })}</p>
                </ConfirmDialog>
            </Portal>
        </VStack>
    )
}
