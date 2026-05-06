import { QueryClient } from '@tanstack/solid-query'

export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 5000,
            gcTime: 5 * 60 * 1000,
            refetchOnWindowFocus: true,
        },
    },
})
