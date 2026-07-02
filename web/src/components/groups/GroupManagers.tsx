import CloseIcon from '@iconify-icons/mdi/close'
import PlusIcon from '@iconify-icons/mdi/plus'
import { createQuery, keepPreviousData, useQueryClient } from '@tanstack/solid-query'
import { useNavigate } from '@tanstack/solid-router'
import { createMemo, createSignal, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '~/components/Button'
import AddTeacherToGroupDialog from '~/components/dialogs/AddTeacherToGroupDialog'
import PaginatedUserList, { type PaginatedUserListHandle } from '~/components/users/PaginatedUserList'
import { useUserDisplayContext } from '~/components/users/UserDisplayContext'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { groupManagersQueryOptions } from '~/queries/groups'
import { debounce } from '~/utils'
import { useGroupDisplayContext } from './GroupDisplayContext'
import type { User } from '~/api'

export interface GroupManagersProps {
    groupId: number
    page: number
    onPageChange: (page: number) => void
}

export function GroupManagers(props: GroupManagersProps) {
    const { client } = useAPI()
    const { string } = useI18n()
    const qc = useQueryClient()
    const navigate = useNavigate()
    const userDisplayContext = useUserDisplayContext()
    const { editable } = useGroupDisplayContext()

    const [query, setQuery] = createSignal<string | undefined>(undefined)
    const [addDialogOpen, setAddDialogOpen] = createSignal(false)
    let listHandle: PaginatedUserListHandle | undefined

    const managersQuery = createQuery(() => ({
        ...groupManagersQueryOptions(client, props.groupId, props.page, query()),
        placeholderData: keepPreviousData,
        notifyOnChangeProps: ['data'],
    }))
    const debouncedSetQuery = createMemo(() => debounce(setQuery, 350))

    const removeUserFromGroup = async (user: User) => {
        try {
            await client.users.admin.patch(user.id, {
                patchLastName: false,
                patchAvatarUrl: false,
                patchMiddleName: false,
                patchPrefix: false,
                patchProgramId: false,
                patchGroups: true,
                // Managers (teachers) can be in any group type, so we just filter the group out of their list.
                groups: user.groups.filter(g => g.id !== props.groupId).map(g => g.id),
            })

            await qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'managers'] })
            listHandle?.onUserRemove(user.id)
        } catch (e) {
            console.error(e)
            alert(`Failed to remove teacher from group: ${e}`)
        }
    }

    return (
        <div style={{ '--sticky-offset': '48px' }}>
            <Show when={editable}>
                <Portal>
                    <AddTeacherToGroupDialog
                        open={addDialogOpen()}
                        onClose={() => setAddDialogOpen(false)}
                        onSuccess={u => listHandle?.onUserAdd(u)}
                        groupId={props.groupId}
                    />
                </Portal>
            </Show>
            <PaginatedUserList
                searchLabel={string.SEARCH_TEACHERS()}
                onSearch={debouncedSetQuery()}
                ref={editable ? h => (listHandle = h) : undefined}
                page={props.page}
                data={managersQuery.data}
                onClick={user => navigate(userDisplayContext.viewLinkProps(user.id))}
                onPageChange={props.onPageChange}
                onPagePreload={page => qc.prefetchQuery(groupManagersQueryOptions(client, props.groupId, page))}
                onRefresh={() => qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'managers'] })}
                headerRight={
                    editable
                        ? () => (
                              <Button onClick={() => setAddDialogOpen(true)} size="xs" icon={PlusIcon}>
                                  {string.ADD_TEACHER()}
                              </Button>
                          )
                        : undefined
                }
                trailing={
                    editable
                        ? props => (
                              <Button
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
