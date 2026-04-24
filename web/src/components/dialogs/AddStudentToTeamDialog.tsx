import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import AddUserDialog from './AddUserDialog'
import type { User } from '../../api'

export default function AddStudentToTeamDialog(props: {
    open: boolean
    onClose: () => unknown
    onSuccess?: (user: User) => unknown
    teamId: number
}) {
    const api = useAPI()
    const { string } = useI18n()

    return (
        <AddUserDialog
            open={props.open}
            onClose={props.onClose}
            onSuccess={props.onSuccess}
            headline={string.ADD_STUDENT_TO_TEAM()}
            actionLabel={string.ADD_STUDENT()}
            idLabel={string.STUDENT_ID()}
            validateUser={user => (!user.isStudent() ? 'Not a student' : null)}
            onConfirm={async user => {
                const currentTeams = user.teams.map(t => t.id)
                if (!currentTeams.includes(props.teamId)) {
                    await api.client.users.admin.patch(user.id, {
                        patchAvatarUrl: false,
                        patchMiddleName: false,
                        patchTeams: true,
                        teams: [...currentTeams, props.teamId],
                    })
                }
            }}
        />
    )
}
