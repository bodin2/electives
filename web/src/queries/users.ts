import { queryOptions } from '@tanstack/solid-query'
import { nonNull } from '~/utils'
import type { Client } from '~/api'

/**
 * @cache refetch
 */
export const studentsQueryOptions = (client: Client<unknown>, page: number, query?: string) =>
    queryOptions({
        queryKey: ['users', 'students', { page, query }] as const,
        queryFn: () => client.users.fetchStudents(page, query),
        staleTime: query ? 5000 : 10000,
    })

/**
 * @cache refetch
 */
export const teachersQueryOptions = (client: Client<unknown>, page: number, query?: string) =>
    queryOptions({
        queryKey: ['users', 'teachers', { page, query }] as const,
        queryFn: () => client.users.fetchTeachers(page, query),
        staleTime: query ? 5000 : 10000,
    })

/**
 * @cache update-in-place
 */
export const userQueryOptions = (client: Client<unknown>, userId: number) =>
    queryOptions({
        queryKey: ['users', userId] as const,
        queryFn: () => client.users.fetch(userId),
    })

/**
 * @cache refetch
 */
export const teacherSubjectsQueryOptions = (client: Client<unknown>, userId: number | '@me') =>
    queryOptions({
        queryKey: ['teacherSubjects', userId === '@me' ? nonNull(client.user).id : userId] as const,
        queryFn: () => client.users.fetchTeacherSubjects(userId),
    })
