import { createTeam } from '../src/utils/teams'

import { error, success } from './shared'

const id = Number(prompt('💳 Team ID (eg. 2568_05):'))
if (!id) error('Team ID is required, and must be an integer.')

const name = prompt('✏️ Name (eg. ม.5 (2568)):')
if (!name) error('Name is required.')

success(
    'Created team:',
    createTeam({
        id,
        name,
    }),
)
