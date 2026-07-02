import { useQueryClient } from '@tanstack/solid-query'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import AddUserDialog from './base/AddUserDialog'
import type { AdminUserPatch, User } from '~/api'

export default function AddTeacherToGroupDialog(props: {
    open: boolean
    onClose: () => unknown
    onSuccess?: (user: User) => unknown
    groupId: number
}) {
    const api = useAPI()
    const qc = useQueryClient()
    const { string } = useI18n()

    return (
        <AddUserDialog
            open={props.open}
            onClose={props.onClose}
            onSuccess={props.onSuccess}
            headline={string.ADD_TEACHER_TO_GROUP()}
            type="teacher"
            onConfirm={async user => {
                if (user.hasGroup(props.groupId)) return

                const patch: AdminUserPatch = {
                    patchLastName: false,
                    patchAvatarUrl: false,
                    patchMiddleName: false,
                    patchPrefix: false,
                    patchGroups: true,
                    patchProgramId: false,
                    groups: [...user.groups.map(g => g.id), props.groupId],
                }

                await api.client.users.admin.patch(user.id, patch)

                await Promise.all([
                    qc.invalidateQueries({ queryKey: ['groups', 'memberCounts'] }),
                    qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'managers'] }),
                ])
            }}
        />
    )
}
