import { Team as TeamProto, User } from '@bodin2/electives-common/proto/api'
import { inferSchema, initParser } from 'udsv'
import { AdminAddUserRequest, type UserType } from '../api/types'
import type { Team } from '../api'

/**
 * Parse a CSV file into AdminAddUserRequest objects.
 *
 * @param text The CSV text content
 * @param type The user type to assign
 * @param cachedTeams A cached list of teams for rendering data
 * @returns An array of user creation requests
 */
export function parseUserCSV(text: string, type: UserType, cachedTeams?: Team[]): AdminAddUserRequest[] {
    const parser = initParser(inferSchema(text, { trim: true }))
    const rows = parser.stringObjs<{ [header: string]: string }>(text)

    const requests: AdminAddUserRequest[] = []

    for (let i = 0; i < rows.length; i++) {
        const row = rows[i]

        const user = User.create({
            type,
            teams: [],
        })

        const req = AdminAddUserRequest.create({
            teamIds: [],
            password: '',
        })

        for (const key in row) {
            const header = key.trim().toLowerCase()
            const value = row[key]?.trim()
            if (!value) continue

            switch (header) {
                case 'id':
                    user.id = Number.parseInt(value, 10)
                    break
                case 'first_name':
                case 'firstname':
                    user.firstName = value
                    break
                case 'middle_name':
                case 'middlename':
                    user.middleName = value
                    break
                case 'last_name':
                case 'lastname':
                    user.lastName = value
                    break
                case 'password':
                    req.password = value
                    break
                case 'teams':
                case 'team_ids': {
                    const teamIdStrings = value.split(',')
                    const teamIds: number[] = []
                    for (let k = 0; k < teamIdStrings.length; k++) {
                        const id = Number.parseInt(teamIdStrings[k].trim(), 10)
                        if (!Number.isNaN(id)) {
                            const team = cachedTeams?.find(team => team.id === id)
                            if (team) {
                                user.teams.push(TeamProto.fromJSON(team))
                            }

                            teamIds.push(id)
                        }
                    }
                    req.teamIds = teamIds
                    break
                }
            }
        }

        req.user = user

        if (user.id !== undefined && !Number.isNaN(user.id) && user.firstName && req.password) {
            requests.push(req)
        }
    }

    return requests
}
