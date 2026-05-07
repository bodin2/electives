import { useQueryClient } from '@tanstack/solid-query'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import AddUserDialog from './base/AddUserDialog'
import type { User } from '../../api'

export default function AddStudentToTeamDialog(props: {
    open: boolean
    onClose: () => unknown
    onSuccess?: (user: User) => unknown
    teamId: number
}) {
    const api = useAPI()
    const qc = useQueryClient()
    const { string } = useI18n()

    return (
        <AddUserDialog
            open={props.open}
            onClose={props.onClose}
            onSuccess={props.onSuccess}
            headline={string.ADD_STUDENT_TO_TEAM()}
            type="student"
            onConfirm={async user => {
                const currentTeams = user.teams.map(t => t.id)
                if (!currentTeams.includes(props.teamId)) {
                    await api.client.users.admin.patch(user.id, {
                        patchLastName: false,
                        patchAvatarUrl: false,
                        patchMiddleName: false,
                        patchTeams: true,
                        teams: [...currentTeams, props.teamId],
                    })

                    await api.client.users.fetch(user.id, { force: true })

                    await Promise.all([
                        qc.invalidateQueries({ queryKey: ['teams', 'memberCounts'] }),
                        qc.invalidateQueries({ queryKey: ['teams', props.teamId, 'members'] }),
                    ])
                }
            }}
        />
    )
}
