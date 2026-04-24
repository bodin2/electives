import { createContext, type ParentProps, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'
import type { LinkProps } from '@tanstack/solid-router'
import type { AdminSubjectPatch, Elective, Subject, User } from '../../api'

export type PatchSetterKey = {
    [K in keyof AdminSubjectPatch]: AdminSubjectPatch[K] extends boolean | undefined ? K : never
}[keyof AdminSubjectPatch]

interface SubjectDisplayContext {
    editable: boolean
    createLinkProps: () => LinkProps
    viewLinkProps: (electiveId: number, subjectId: number) => LinkProps
    editLinkProps: (subjectId: number) => LinkProps
    setUser: (user: User | undefined) => void
    setElective: (elective: Elective | undefined) => void
    setDeletingSubject: (subject: Subject | undefined) => void
    setSubject: (subject: Subject | undefined) => void
    setOnEdit: (onEdit: SubjectDisplayContext['onEdit']) => void
    setOnSave: (onSave: SubjectDisplayContext['onSave']) => void
    deletingSubject?: Subject
    elective?: Elective
    user?: User
    subject?: Subject
    onEdit?: (field: string, value: any, patchKey?: PatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
}

const SubjectDisplayContext = createContext<SubjectDisplayContext>(null as unknown as SubjectDisplayContext)

export function SubjectDisplayContextProvider(
    props: ParentProps<{
        value: Omit<
            SubjectDisplayContext,
            'setUser' | 'setElective' | 'setDeletingSubject' | 'setSubject' | 'setOnEdit' | 'setOnSave'
        >
    }>,
) {
    const [value, setValue] = createStore<SubjectDisplayContext>({
        user: undefined,
        elective: undefined,
        deletingSubject: undefined,
        subject: undefined,
        onEdit: undefined,
        onSave: undefined,
        ...props.value,
        setUser: u => setValue('user', u),
        setElective: e => setValue('elective', e),
        setDeletingSubject: s => setValue('deletingSubject', s),
        setSubject: s => setValue('subject', s),
        setOnEdit: f => setValue('onEdit', () => f),
        setOnSave: f => setValue('onSave', () => f),
    } as SubjectDisplayContext)

    return <SubjectDisplayContext.Provider value={value}>{props.children}</SubjectDisplayContext.Provider>
}

export const useSubjectDisplayContext = () => useContext(SubjectDisplayContext)

export const BaseSubjectDisplayContext = {
    createLinkProps: () => ({
        to: '/manage/subjects/$subjectId',
        params: { subjectId: 'new' },
    }),
    editLinkProps: (subjectId: number) => ({
        to: '/manage/subjects/$subjectId',
        params: { subjectId },
    }),
    viewLinkProps: (electiveId: number, subjectId: number) => ({
        to: '/enroll/$electiveId/$subjectId',
        params: { electiveId, subjectId },
    }),
    editable: false,
} as const satisfies Partial<SubjectDisplayContext>
