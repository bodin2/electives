import type { Elysia } from 'elysia'

export const StatusServiceGroup = '/status'

const StatusService = (app: Elysia) =>
    app.group(StatusServiceGroup, app =>
        app
            // HEAD /status
            .head('/', ({ set }) => {
                set.status = 200
            }),
    )

export default StatusService
