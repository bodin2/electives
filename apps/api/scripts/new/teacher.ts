import { createTeacher } from '../../src/utils/users/teachers'

import { error, success } from '../shared'

const id = Number(prompt('💳 Teacher ID:'))
if (!id) error('Teacher ID is required, and must be an integer.')

const firstName = prompt('✏️ First name:')
if (!firstName) error('First name is required.')

const middleName = prompt('✏️ Middle name (optional):')

const lastName = prompt('✏️ Last name:')
if (!lastName) error('Last name is required.')

const password = prompt('🔑 Password:')
if (!password) error('Password is required.')

success(
    'Created teacher:',
    await createTeacher(
        {
            id,
            firstName,
            middleName: middleName || null,
            lastName,
        },
        password,
    ),
)
