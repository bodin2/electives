import { createContext, type ParentProps, useContext } from 'solid-js'
import type { LinkProps } from '@tanstack/solid-router'
import type { AdminUserPatch, RawUser } from '~/api'

export type UserPatchSetterKey = {
    [K in keyof AdminUserPatch]: AdminUserPatch[K] extends boolean | undefined ? K : never
}[keyof AdminUserPatch]

export interface UserData extends RawUser {
    newPassword?: string
}

export interface UserDisplayContext {
    editable: boolean
    createLinkProps: (type?: 'student' | 'teacher') => LinkProps
    viewLinkProps: (userId: number) => LinkProps
    editLinkProps: (userId: number) => LinkProps
}

const UserDisplayContext = createContext<UserDisplayContext>(null as unknown as UserDisplayContext)

export function UserDisplayContextProvider(
    props: ParentProps<{
        value: UserDisplayContext
    }>,
) {
    return <UserDisplayContext.Provider value={props.value}>{props.children}</UserDisplayContext.Provider>
}

export const useUserDisplayContext = () => useContext(UserDisplayContext)

export const BaseUserDisplayContext = {
    createLinkProps: () => ({}),
    editLinkProps: () => ({}),
    viewLinkProps: () => ({}),
    editable: false,
} as const satisfies Partial<UserDisplayContext>
