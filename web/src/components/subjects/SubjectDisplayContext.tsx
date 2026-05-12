import { createContext, type ParentProps, useContext } from 'solid-js'
import type { LinkProps } from '@tanstack/solid-router'
import type { AdminSubjectPatch } from '~/api'

export type PatchSetterKey = {
    [K in keyof AdminSubjectPatch]: AdminSubjectPatch[K] extends boolean | undefined ? K : never
}[keyof AdminSubjectPatch]

export interface SubjectDisplayContext {
    editable: boolean
    createLinkProps: () => LinkProps
    viewLinkProps: (enrollmentId: number, subjectId: number, tab?: string) => LinkProps
    editLinkProps: (subjectId: number) => LinkProps
}

const SubjectDisplayContext = createContext<SubjectDisplayContext>(null as unknown as SubjectDisplayContext)

export function SubjectDisplayContextProvider(
    props: ParentProps<{
        value: SubjectDisplayContext
    }>,
) {
    return <SubjectDisplayContext.Provider value={props.value}>{props.children}</SubjectDisplayContext.Provider>
}

export const useSubjectDisplayContext = () => useContext(SubjectDisplayContext)

export const BaseSubjectDisplayContext = {
    createLinkProps: () => ({
        to: '/manage/subjects/$subjectId',
        params: { subjectId: 'new' },
    }),
    editLinkProps: subjectId => ({
        to: '/manage/subjects/$subjectId',
        params: { subjectId },
    }),
    viewLinkProps: (enrollmentId, subjectId, tab?: string) => ({
        to: '/enroll/$enrollmentId/$subjectId',
        params: { enrollmentId, subjectId },
        search: { tab },
    }),
    editable: false,
} as const satisfies SubjectDisplayContext
