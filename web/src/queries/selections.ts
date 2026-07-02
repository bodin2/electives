import { queryOptions } from '@tanstack/solid-query'
import { nonNull } from '~/utils'
import type { Client } from '~/api'

/**
 * @cache refetch
 */
export const selectionsQueryOptions = (client: Client<unknown>, userId: number | '@me') =>
    queryOptions({
        queryKey: ['selections', userId === '@me' ? nonNull(client.user).id : userId] as const,
        queryFn: () => client.selections.fetch(userId),
    })
