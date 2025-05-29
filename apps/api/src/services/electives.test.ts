import { ElectivesService_ListResponse, ElectivesService_ListSubjectsResponse } from '@bodin2/electives-proto/api'

import { describe, expect, test } from 'bun:test'

import { BaseUrl, authenticate } from '../__tests__/shared'

import ElectivesService from './electives'

const [token] = await authenticate()

describe(ElectivesService.Group, () => {
    const Route = `${BaseUrl}/${ElectivesService.Group}`

    test.skipIf(!token)('GET -> 200', async () => {
        const res = await fetch(Route, {
            method: 'GET',
            headers: {
                Authorization: `Bearer ${token}`,
            },
        })

        const { electives } = ElectivesService_ListResponse.decode(new Uint8Array(await res.arrayBuffer()))
        expect(Array.isArray(electives)).toBe(true)

        describe(`${ElectivesService.Group}/:id/subjects`, () => {
            test.skipIf(!electives.length)('GET -> 200', async () => {
                const SubjectsRoute = `${Route}/${electives[0].id}/subjects`
                const res = await fetch(SubjectsRoute, {
                    method: 'GET',
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                })

                const { subjects } = ElectivesService_ListSubjectsResponse.decode(
                    new Uint8Array(await res.arrayBuffer()),
                )
                expect(Array.isArray(subjects)).toBe(true)
            })
        })
    })
})
