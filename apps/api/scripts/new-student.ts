import { createStudent } from '../src/utils/users'

import { error, success } from './shared'

const id = Number(prompt('💳 Student ID:'))
if (!id) error('Student ID is required, and must be an integer.')

const firstName = prompt('✏️ First name:')
if (!firstName) error('First name is required.')

const middleName = prompt('✏️ Middle name (optional):')

const lastName = prompt('✏️ Last name:')
if (!lastName) error('Last name is required.')

const teamId = Number(prompt('👥 Team ID (eg. 2568_05): '))
if (!teamId) error('Team ID is required, and must be an integer.')

const password = prompt('🔑 Password:')
if (!password) error('Password is required.')

success(
    'Created student:',
    await createStudent(
        {
            firstName,
            middleName: middleName || null,
            lastName,
            id,
            teamId,
            sessionHash: null,
        },
        password,
    ),
)
