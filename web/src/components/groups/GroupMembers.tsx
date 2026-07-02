import CloseIcon from '@iconify-icons/mdi/close'
import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import SwapHorizontalIcon from '@iconify-icons/mdi/swap-horizontal'
import { useNavigate } from '@tanstack/solid-router'
import { createQuery, keepPreviousData, skipToken, useQueryClient } from '@tanstack/solid-query'
import { createMemo, createSignal, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { GroupType, type User } from '~/api'
import PaginatedUserList, { type PaginatedUserListHandle } from '~/components/users/PaginatedUserList'
import { Button } from '~/components/Button'
import AddStudentToGroupDialog from '~/components/dialogs/AddStudentToGroupDialog'
import { ConfirmDialog } from '~/components/dialogs/base/ConfirmDialog'
import { SelectGroupDialog } from '~/components/dialogs/SelectGroupDialog'
import { GroupMembersFilterChip } from '~/components/enrollments/GroupMembersFilterChip'
import { HStack } from '~/components/Stack'
import { useUserDisplayContext } from '~/components/users/UserDisplayContext'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { enrollmentUnenrolledMembersQueryOptions } from '~/queries/enrollments'
import { groupMembersQueryOptions, groupQueryOptions, groupsQueryOptions } from '~/queries/groups'
import { debounce } from '~/utils'
import { useGroupDisplayContext } from './GroupDisplayContext'

export interface GroupMembersProps {
    groupId: number
    page: number
    onPageChange: (page: number) => void
}

export function GroupMembers(props: GroupMembersProps) {
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()
    const navigate = useNavigate()
    const userDisplayContext = useUserDisplayContext()
    const { editable } = useGroupDisplayContext()

    const [query, setQuery] = createSignal<string | undefined>(undefined)
    const [filterEnrollmentId, setFilterEnrollmentId] = createSignal<number | null>(null)
    const isFiltered = () => filterEnrollmentId() !== null

    const [addDialogOpen, setAddDialogOpen] = createSignal(false)
    const [deleteDialogOpen, setDeleteDialogOpen] = createSignal(false)
    const [migrateDialogOpen, setMigrateDialogOpen] = createSignal(false)
    let listHandle: PaginatedUserListHandle | undefined

    const groupQuery = createQuery(() => ({
        ...groupQueryOptions(client, props.groupId),
        notifyOnChangeProps: ['data'],
        enabled: editable,
    }))

    const allGroupsQuery = createQuery(() => ({
        ...groupsQueryOptions(client),
        // Only needed once the user opens the migrate dialog
        enabled: editable && migrateDialogOpen(),
        notifyOnChangeProps: ['data'],
    }))

    // Same-type groups, excluding the current one
    const migrateCandidates = createMemo(() => {
        if (!allGroupsQuery.isSuccess || !groupQuery.isSuccess) return []
        const all = allGroupsQuery.data
        const currentType = groupQuery.data.type
        if (currentType === undefined) return []
        return all.filter(g => g.type === currentType && g.id !== props.groupId)
    })

    // @ts-expect-error: TypeScript moment
    const isFixedGroup = () => [GroupType.PROGRAM, GroupType.CUSTOM].includes(groupQuery.data?.type)

    const membersQuery = createQuery(() => ({
        ...groupMembersQueryOptions(client, props.groupId, props.page, query()),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data', 'isFetching'],
        enabled: !isFiltered(),
    }))
    const unenrolledQuery = createQuery(() => ({
        ...enrollmentUnenrolledMembersQueryOptions(
            client,
            filterEnrollmentId() ?? 0,
            isFiltered() ? props.groupId : skipToken,
            props.page,
        ),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data', 'isFetching', 'isSuccess'],
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    const activeData = () =>
        isFiltered()
            ? unenrolledQuery.isSuccess && !unenrolledQuery.isFetching
                ? unenrolledQuery.data
                : membersQuery.data
            : membersQuery.data

    const hasNoMembers = () => membersQuery.isSuccess && membersQuery.data?.users.length === 0

    const handleDeleteMembers = async () => {
        try {
            await client.groups.admin.deleteMembers(props.groupId)
            await Promise.all([
                qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'members'] }),
                qc.invalidateQueries({ queryKey: ['groups', 'memberCounts'] }),
            ])
        } catch (e) {
            console.error(e)
            alert(string.ERROR_DELETE_FAILED({ error: String(e) }))
        }
    }

    const handleMigrate = async (targetGroupId: number | null) => {
        if (targetGroupId === null) return
        try {
            await client.groups.admin.migrateMembers(props.groupId, targetGroupId)
            await Promise.all([
                qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'members'] }),
                qc.invalidateQueries({ queryKey: ['groups', targetGroupId, 'members'] }),
                qc.invalidateQueries({ queryKey: ['groups', 'memberCounts'] }),
            ])
        } catch (e) {
            console.error(e)
            alert(string.ERROR_SAVE_FAILED({ error: String(e) }))
        }
    }

    const removeUserFromGroup = async (user: User) => {
        try {
            if (!isFixedGroup()) {
                // Fixed slotted (GRADE/ROOM) memberships can't be removed directly
                // The student must be reassigned to a different group of the same type via the user details page
                // The remove button is hidden for those groups (see below), so this is just a defensive guard
                throw new Error(
                    'Cannot remove a member from a non-CUSTOM group; migrate all members to a different group of the same type first.',
                )
            }

            await client.users.admin.patch(user.id, {
                patchLastName: false,
                patchAvatarUrl: false,
                patchMiddleName: false,
                patchPrefix: false,
                patchProgramId: false,
                patchGroups: true,
                // `groups` on UserPatch is the replacement list of CUSTOM-typed memberships.
                groups: user.customGroups.filter(g => g.id !== props.groupId).map(g => g.id),
            })
            listHandle?.onUserRemove(user.id)
        } catch (e) {
            console.error(e)
            alert(`Failed to remove user from group: ${e}`)
        }
    }

    return (
        <div style={{ '--sticky-offset': '48px' }}>
            <Show when={editable}>
                <Portal>
                    <AddStudentToGroupDialog
                        open={addDialogOpen()}
                        onClose={() => setAddDialogOpen(false)}
                        onSuccess={u => listHandle?.onUserAdd(u)}
                        groupId={props.groupId}
                        groupType={groupQuery.data?.type ?? GroupType.CUSTOM}
                    />
                    <ConfirmDialog
                        open={deleteDialogOpen()}
                        variant="danger"
                        closedBy="any"
                        onCancel={() => setDeleteDialogOpen(false)}
                        onConfirm={async () => {
                            await handleDeleteMembers()
                            setDeleteDialogOpen(false)
                        }}
                        confirmText={string.DELETE_MEMBERS()}
                        headline={string.DELETE_MEMBERS()}
                    >
                        <p>
                            {string.CONFIRM_DELETE_MEMBERS({ name: <strong>{groupQuery.data?.name ?? ''}</strong> })}
                        </p>
                    </ConfirmDialog>
                    <SelectGroupDialog
                        open={migrateDialogOpen()}
                        onClose={() => setMigrateDialogOpen(false)}
                        onSave={handleMigrate}
                        groups={migrateCandidates()}
                        value={null}
                        headline={string.MIGRATE_MEMBERS()}
                        description={string.MIGRATE_MEMBERS_HINT()}
                        showReset={false}
                    />
                </Portal>
            </Show>
            <PaginatedUserList
                isFetching={membersQuery.isFetching || unenrolledQuery.isFetching}
                searchLabel={string.SEARCH_STUDENTS()}
                // The unenrolled-members endpoint doesn't support server-side search
                onSearch={isFiltered() ? undefined : debouncedSetQuery()}
                ref={editable ? h => (listHandle = h) : undefined}
                page={props.page}
                data={activeData()}
                onPageChange={props.onPageChange}
                onPagePreload={page =>
                    isFiltered() ? undefined : qc.prefetchQuery(groupMembersQueryOptions(client, props.groupId, page))
                }
                onRefresh={() =>
                    isFiltered()
                        ? qc.invalidateQueries({
                              queryKey: ['enrollments', filterEnrollmentId(), 'unenrolledMembers'],
                          })
                        : qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'members'] })
                }
                filters={() => (
                    <GroupMembersFilterChip
                        groupId={props.groupId}
                        value={filterEnrollmentId()}
                        onChange={v => {
                            setFilterEnrollmentId(v)
                            props.onPageChange(1)
                        }}
                    />
                )}
                onClick={user => navigate(userDisplayContext.viewLinkProps(user.id))}
                headerRight={
                    editable
                        ? () =>
                              !isFiltered() && (
                                  <HStack gap={8} alignVertical="center" wrap>
                                      <Button
                                          disabled={hasNoMembers()}
                                          onClick={() => setDeleteDialogOpen(true)}
                                          size="xs"
                                          variant="tonal-error"
                                          icon={DeleteIcon}
                                      >
                                          {string.DELETE_MEMBERS()}
                                      </Button>
                                      <Button
                                          disabled={hasNoMembers()}
                                          onClick={() => setMigrateDialogOpen(true)}
                                          size="xs"
                                          variant="tonal"
                                          icon={SwapHorizontalIcon}
                                      >
                                          {string.MIGRATE_MEMBERS()}
                                      </Button>
                                      <Button onClick={() => setAddDialogOpen(true)} size="xs" icon={PlusIcon}>
                                          {string.ADD_STUDENT()}
                                      </Button>
                                  </HStack>
                              )
                        : undefined
                }
                trailing={
                    editable && !isFiltered()
                        ? props => (
                              <Button
                                  disabled={!isFixedGroup()}
                                  aria-label={string.REMOVE()}
                                  size="xs"
                                  variant="tonal-error"
                                  onClick={e => {
                                      e.stopPropagation()
                                      return removeUserFromGroup(props.user)
                                  }}
                                  icon={CloseIcon}
                                  iconType="only"
                              />
                          )
                        : undefined
                }
            />
        </div>
    )
}
