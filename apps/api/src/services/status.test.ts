import { describe, expect, test } from 'bun:test'

import { BaseUrl } from '../__tests__/shared'
import StatusService from './status'

describe(StatusService.Group, () => {
    const Route = `${BaseUrl}${StatusService.Group}`

    test('HEAD -> 200', () => expect(fetch(Route, { method: 'HEAD' })).resolves.toHaveProperty('status', 200))
    test('ALL -> 404', () => expect(fetch(Route, { method: 'GET' })).resolves.toHaveProperty('status', 404))
})
