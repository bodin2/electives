import {
    ElectivesService_ListResponse,
    ElectivesService_ListSubjectsResponse,
    SubjectTag,
} from '@bodin2/electives-proto/api'

import { Elysia, t } from 'elysia'
import { HttpError } from 'elysia-http-error'
import { protobuf } from 'elysia-protobuf'

import { getStudentElectives } from '../utils/users/students'
import { getAllElectives, getElectiveSubjects, type Elective } from '../utils/electives'

import { authenticator } from './auth'

import type { Elective as ElectiveProto } from '@bodin2/electives-proto/api'

const mapElectiveToProto = ({ id, name, startDate, endDate }: Elective): ElectiveProto => ({
    id,
    name,
    startDate: startDate ? Math.ceil(startDate.getTime() / 1000) : undefined,
    endDate: endDate ? Math.floor(endDate.getTime() / 1000) : undefined,
})

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
        // GET /electives?limit={number}&offset={number}
        .get(
            '/',
            async ({ user, query: { limit, offset } }) => {
                if (user.student)
                    return {
                        electives: (await getStudentElectives(user.id, limit, offset)).map(mapElectiveToProto),
                    }

                if (user.teacher)
                    return {
                        electives: (await getAllElectives(limit, offset)).map(mapElectiveToProto),
                    }

                // This should never happen, but in case a ghost user tries to access this route...
                throw HttpError.Forbidden()
            },
            {
                query: t.Object({
                    limit: t.Optional(t.Integer({ minimum: 1, maximum: 100 })),
                    offset: t.Optional(t.Integer({ minimum: 0 })),
                }),
                parse: 'none',
                responseSchema: 'list',
            },
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
