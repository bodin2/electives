import { queryOptions } from '@tanstack/solid-query'
import type { Client } from '../api'

export const selectionsQueryOptions = (client: Client<unknown>, userId: number | '@me') =>
    queryOptions({
        queryKey: ['selections', userId] as const,
        queryFn: () => client.selections.fetch(userId),
    })
