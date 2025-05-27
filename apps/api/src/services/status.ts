import { Elysia } from 'elysia'

const StatusService = () =>
    new Elysia({ prefix: StatusService.Group })
        // HEAD /status
        .head('/', ({ status }) => status(200))

StatusService.Group = '/status'

export default StatusService
