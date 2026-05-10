import { useQueryClient } from '@tanstack/solid-query'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import AddUserDialog from './base/AddUserDialog'
import type { User } from '../../api'

export default function AddStudentToGroupDialog(props: {
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
            headline={string.ADD_STUDENT_TO_GROUP()}
            type="student"
            onConfirm={async user => {
                const currentGroups = user.groups.map(g => g.id)
                if (!currentGroups.includes(props.groupId)) {
                    await api.client.users.admin.patch(user.id, {
                        patchLastName: false,
                        patchAvatarUrl: false,
                        patchMiddleName: false,
                        patchPrefix: false,
                        patchGroups: true,
                        groups: [...currentGroups, props.groupId],
                    })

                    await api.client.users.fetch(user.id, { force: true })

                    await Promise.all([
                        qc.invalidateQueries({ queryKey: ['groups', 'memberCounts'] }),
                        qc.invalidateQueries({ queryKey: ['groups', props.groupId, 'members'] }),
                    ])
                }
            }}
        />
    )
}
