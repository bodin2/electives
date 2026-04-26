import { createContext, type ParentProps, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'
import type { LinkProps } from '@tanstack/solid-router'
import type { AdminUserPatch, RawUser, User } from '../../api'

export type UserPatchSetterKey = {
    [K in keyof AdminUserPatch]: AdminUserPatch[K] extends boolean | undefined ? K : never
}[keyof AdminUserPatch]

export interface UserData extends RawUser {
    newPassword?: string
}

interface UserDisplayContext {
    creating: boolean
    editable: boolean
    edited: boolean
    createLinkProps: (type?: 'student' | 'teacher') => LinkProps
    viewLinkProps: (userId: number) => LinkProps
    editLinkProps: (userId: number) => LinkProps
    setUser: (user: User | undefined) => void
    setUserData: (data: UserData | undefined) => void
    setCreating: (creating: boolean) => void
    setEdited: (edited: boolean) => void
    setOnEdit: (onEdit: UserDisplayContext['onEdit']) => void
    setOnSave: (onSave: UserDisplayContext['onSave']) => void
    setOnDelete: (onDelete: UserDisplayContext['onDelete']) => void
    user?: User
    userData?: UserData
    onEdit?: (field: string, value: unknown, patchKey?: UserPatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
}

const UserDisplayContext = createContext<UserDisplayContext>(null as unknown as UserDisplayContext)

export function UserDisplayContextProvider(
    props: ParentProps<{
        value: Omit<
            UserDisplayContext,
            'setUser' | 'setUserData' | 'setCreating' | 'setOnEdit' | 'setOnSave' | 'setOnDelete' | 'setEdited'
        >
    }>,
) {
    const [value, setValue] = createStore<UserDisplayContext>({
        user: undefined,
        userData: undefined,
        onEdit: undefined,
        onSave: undefined,
        ...props.value,
        setEdited: edited => setValue('edited', edited),
        setUser: u => setValue('user', u),
        setUserData: d => setValue('userData', d),
        setCreating: c => setValue('creating', c),
        setOnEdit: f => setValue('onEdit', () => f),
        setOnSave: f => setValue('onSave', () => f),
        setOnDelete: f => setValue('onDelete', () => f),
    } as UserDisplayContext)

    return <UserDisplayContext.Provider value={value}>{props.children}</UserDisplayContext.Provider>
}

export const useUserDisplayContext = () => useContext(UserDisplayContext)

export const BaseUserDisplayContext = {
    createLinkProps: type => ({
        to: '/manage/users/$userId',
        params: { userId: 'new' },
        search: { type },
    }),
    editLinkProps: userId => ({
        to: '/manage/users/$userId',
        params: { userId: String(userId) },
    }),
    viewLinkProps: userId => ({
        to: '/manage/users/$userId',
        params: { userId: String(userId) },
    }),
    creating: false,
    editable: false,
    edited: false,
} as const satisfies Partial<UserDisplayContext>
