import {
    ElectivesService_ListResponse,
    ElectivesService_ListSubjectsResponse,
    SubjectTag,
} from '@bodin2/electives-proto/api'

import { Elysia, t } from 'elysia'
import { protobuf } from 'elysia-protobuf'

import { getStudentElectives } from '../utils/users/students'
import { authenticator } from './auth'

import type { Elective } from '@bodin2/electives-proto/api'
import { getElectiveSubjects } from '../utils/electives'

const ElectivesService = () =>
    new Elysia({ prefix: ElectivesService.Group })
        .use(authenticator)
        .use(
            protobuf({
                schemas: {
                    list: ElectivesService_ListResponse,
                    listSubjects: ElectivesService_ListSubjectsResponse,
                },
            }),
        )
        // GET /electives
        .get(
            '/',
            async ({ user }) => {
                return {
                    electives: await getStudentElectives(user.id).then(it =>
                        it.map(
                            ({ id, name, startDate, endDate }) =>
                                ({
                                    id,
                                    name,
                                    startDate: (startDate && Math.ceil(startDate.getTime() / 1000)) ?? undefined,
                                    endDate: (endDate && Math.floor(endDate.getTime() / 1000)) ?? undefined,
                                }) satisfies Elective,
                        ),
                    ),
                }
            },
            { parse: 'none', responseSchema: 'list' },
        )
        .group(
            '/:id',
            {
                params: t.Object({
                    id: t.Integer(),
                }),
            },
            app =>
                app
                    // GET /electives/:id/subjects
                    // This route does not need user guarding, as subject information is public.
                    .get(
                        '/subjects',
                        async ({ params: { id }, query: { tag, limit, offset } }) => {
                            return {
                                subjects: await getElectiveSubjects(id, tag, limit, offset),
                            } satisfies ElectivesService_ListSubjectsResponse
                        },
                        {
                            parse: 'none',
                            responseSchema: 'listSubjects',
                            query: t.Object({
                                tag: t.Optional(t.Enum(SubjectTag)),
                                limit: t.Optional(t.Integer({ minimum: 1, maximum: 100 })),
                                offset: t.Optional(t.Integer({ minimum: 0 })),
                            }),
                        },
                    ),
        )

ElectivesService.Group = '/electives'

export default ElectivesService
