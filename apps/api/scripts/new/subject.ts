import { SubjectTag } from '@bodin2/electives-proto/api'
import { createSubject } from '../../src/utils/subjects'

import { error, success } from '../shared'

const id = prompt('💳 Subject ID (eg. ก12345):')
if (!id) error('Subject ID is required, and must be an integer.')

const name = prompt('✏️ Name:')
if (!name) error('Name is required.')

const electiveId = Number(prompt('📚 Elective ID (eg. 2568_3_1):'))
if (!electiveId) error('Elective ID is required.')

const description = prompt('📖 Description:')
if (!description) error('Description is required.')

const location = prompt('📍 Location:')
if (!location) error('Location is required.')

const maxStudents = Number(prompt('👥 Max students:'))
if (!maxStudents || maxStudents < 1) error('Max students is required, and must be a positive integer.')

const tag = SubjectTag[prompt('🏷️ Tag (optional):') as keyof typeof SubjectTag] ?? SubjectTag.UNRECOGNIZED
if (tag === SubjectTag.UNRECOGNIZED || typeof tag === 'string')
    error(
        `Tag must be one of ${Object.values(SubjectTag)
            .filter(v => typeof v === 'string')
            .join(', ')}.`,
    )

const teamIdRaw = prompt('🏫 Team ID (optional) (eg. 2568_3):')
const teamId = teamIdRaw ? Number(teamIdRaw) : undefined

if (teamId !== undefined && !Number.isInteger(teamId)) error('Team ID must be an integer.')

success('Created subject:', createSubject(id, electiveId, name, description, location, maxStudents, tag, teamId))
