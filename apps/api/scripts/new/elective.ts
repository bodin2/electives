import { createElective } from '../../src/utils/electives'

import { error, success } from '../shared'

const id = Number(prompt('💳 Elective ID (eg. 2568, 2568_3_1):'))
if (!id) error('Elective ID is required, and must be an integer.')

const name = prompt('✏️ Name (eg. วิชาเลือกเสรี ม.3 1/2568):')
if (!name) error('Name is required.')

const teamIdRaw = prompt('🏫 Team ID (optional) (eg. 2568_3_1):')
const teamId = teamIdRaw ? Number(teamIdRaw) : undefined

if (teamId !== undefined && !Number.isInteger(teamId)) error('Team ID must be an integer.')

// TODO: Start and end dates

success('Created elective:', createElective(id, name, teamId))
