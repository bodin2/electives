import { queryOptions } from '@tanstack/solid-query'
import type { Client } from '../api'

export const studentsQueryOptions = (client: Client<unknown>, page: number, query?: string) =>
    queryOptions({
        queryKey: ['admin', 'students', { page, query }] as const,
        queryFn: () => client.users.admin.fetchStudents(page, query),
        staleTime: query ? 5000 : 10000,
    })

export const teachersQueryOptions = (client: Client<unknown>, page: number, query?: string) =>
    queryOptions({
        queryKey: ['admin', 'teachers', { page, query }] as const,
        queryFn: () => client.users.admin.fetchTeachers(page, query),
        staleTime: query ? 5000 : 10000,
    })

export const userQueryOptions = (client: Client<unknown>, userId: number) =>
    queryOptions({
        queryKey: ['users', userId] as const,
        queryFn: () => client.users.fetch(userId),
    })

export const teacherSubjectsQueryOptions = (client: Client<unknown>, userId: number | '@me') =>
    queryOptions({
        queryKey: ['teacherSubjects', userId] as const,
        queryFn: () => client.users.fetchTeacherSubjects(userId),
    })
