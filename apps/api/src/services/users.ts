import { User, UserType } from '@bodin2/electives-proto/api'

import { Elysia, t } from 'elysia'
import { HttpError } from 'elysia-http-error'
import { protobuf } from 'elysia-protobuf'

import { fetchUserById } from '../utils/users'

import { authenticator } from './auth'

const UsersService = () =>
    new Elysia({ prefix: UsersService.Group })
        .use(authenticator)
        .use(
            protobuf({
                schemas: {
                    user: User,
                },
            }),
        )
        .group(
            '/:id',
            {
                params: t.Object({
                    id: t.Union([t.Integer(), t.String({ pattern: '@me' })]),
                }),
            },
            app =>
                app
                    // GET /:id
                    .get(
                        '/',
                        async ({ user, params: { id } }) => {
                            try {
                                const u = id === '@me' ? user : await fetchUserById(id as User['id'])
                                return {
                                    id: u.id,
                                    firstName: u.firstName,
                                    middleName: u.middleName ?? undefined,
                                    lastName: u.lastName,
                                    type: u.student ? UserType.STUDENT : UserType.TEACHER,
                                    avatar: u.teacher.avatar ?? undefined,
                                } satisfies User
                            } catch (e) {
                                if (e instanceof ReferenceError) throw HttpError.NotFound()
                                console.trace(`Error while fetching user "${id}":`, e)
                                throw HttpError.Internal()
                            }
                        },
                        { parse: 'protobuf', responseSchema: 'user' },
                    ),
        )

UsersService.Group = '/users'

export default UsersService
