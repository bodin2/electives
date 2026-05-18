import { queryOptions } from '@tanstack/solid-query'
import type { Client } from '~/api'

/**
 * @cache refetch (warning: may need to refetch `@me` and `student.id`)
 */
export const selectionsQueryOptions = (client: Client<unknown>, userId: number | '@me') =>
    queryOptions({
        queryKey: ['selections', userId] as const,
        queryFn: () => client.selections.fetch(userId),
    })
