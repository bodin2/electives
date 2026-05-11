import { useQueryClient } from '@tanstack/solid-query'
import { GroupType, type User } from '../../api'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import AddUserDialog from './base/AddUserDialog'
import type { AdminUserPatch } from '../../api'

export default function AddStudentToGroupDialog(props: {
    open: boolean
    onClose: () => unknown
    onSuccess?: (user: User) => unknown
    groupId: number
    groupType: GroupType
}) {
    const api = useAPI()
    const qc = useQueryClient()
    const { string } = useI18n()

    return (
        <AddUserDialog
            open={props.open}
            onClose={props.onClose}
            onSuccess={props.onSuccess}
            headline={string.ADD_STUDENT_TO_GROUP()}
            type="student"
            onConfirm={async user => {
                // Already a member of this exact group\
                if (user.hasGroup(props.groupId)) return

                const patch: AdminUserPatch = {
                    patchLastName: false,
                    patchAvatarUrl: false,
                    patchMiddleName: false,
                    patchPrefix: false,
                    patchGroups: false,
                    patchProgramId: false,
                    groups: [],
                }

                switch (props.groupType) {
                    case GroupType.CUSTOM:
                        // CUSTOM-only replacement list: keep existing customs + add this one.
                        patch.patchGroups = true
                        patch.groups = [...user.customGroups.map(g => g.id), props.groupId]
                        break
                    case GroupType.GRADE:
                        patch.gradeId = props.groupId
                        break
                    case GroupType.ROOM:
                        patch.roomId = props.groupId
                        break
                    case GroupType.PROGRAM:
                        patch.programId = props.groupId
                        patch.patchProgramId = true
                        break
                }

                await api.client.users.admin.patch(user.id, patch)

                await Promise.all([
                    qc.invalidateQueries({ queryKey: ['groups', 'memberCounts'] }),
                    qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'members'] }),
                ])
            }}
        />
    )
}
