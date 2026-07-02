import {
    type Component,
    createContext,
    createRenderEffect,
    createSignal,
    Match,
    type ParentProps,
    Show,
    Switch,
    useContext,
} from 'solid-js'
import { createStore } from 'solid-js/store'
import { useTabPersistence } from '~/hooks/useTabPersistence'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { VStack } from '../Stack'
import StickyTabs from '../StickyTabs'
import StudentSelectionsTab from './StudentSelectionsTab'
import TeacherSubjectsTab from './TeacherSubjectsTab'
import UserBottomActions from './UserBottomActions'
import UserDetailsTab from './UserDetailsTab'
import styles from './UserInfo.module.css'
import type { Group, User, UserType } from '~/api'
import type { UserData, UserPatchSetterKey } from './UserDisplayContext'

export interface UserInfoProps {
    user?: User
    userData?: UserData
    editable?: boolean
    creating?: boolean
    edited?: boolean
    onEdit?: (field: string, value: unknown, patchKey?: UserPatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
    extraActions?: Component
    initialType?: UserType
    groups?: Group[]
    persistTab?: boolean
}

export interface UserInfoContext {
    user?: User
    userData?: UserData
    editable?: boolean
    creating?: boolean
    edited?: boolean
    onEdit?: (field: string, value: unknown, patchKey?: UserPatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
}

const UserInfoContext = createContext<UserInfoContext>(null as unknown as UserInfoContext)
export const useUserInfoContext = () =>
    nonNull(useContext(UserInfoContext), 'useUserInfoContext must be used within a UserInfo provider')

export function UserInfoContextProvider(props: ParentProps<{ value: UserInfoContext }>) {
    return <UserInfoContext.Provider value={props.value}>{props.children}</UserInfoContext.Provider>
}

export default function UserInfo(props: UserInfoProps) {
    const { string } = useI18n()

    const [tab, setTab] = createSignal('info')
    useTabPersistence(tab, setTab, { disabled: props.persistTab === false })

    // SolidJS moment
    const [info, setInfo] = createStore<UserInfoContext>(null as unknown as UserInfoContext)
    createRenderEffect(() => {
        setInfo({
            user: props.user,
            userData: props.userData,
            editable: props.editable,
            creating: props.creating,
            edited: props.edited,
            onEdit: props.onEdit,
            onSave: props.onSave,
            onDelete: props.onDelete,
        })
    })

    const tabs = () => {
        const list = [{ label: string.USER_INFO(), value: 'info' }]
        if (!props.creating) {
            if (props.user?.isStudent()) {
                list.push({ label: string.SELECTIONS(), value: 'selections' })
            }

            if (props.user?.isTeacher()) {
                list.push({ label: string.SUBJECTS(), value: 'subjects' })
            }
        }
        return list
    }

    return (
        <UserInfoContext.Provider value={info}>
            <Show when={props.user}>
                <Show when={tabs().length > 1}>
                    <StickyTabs value={tab()} onChange={setTab} class={styles.tabs} tabs={tabs()} />
                </Show>
            </Show>
            <VStack gap={16} grow class={`padded ${styles.tabContent}`} style={{ '--sticky-offset': '48px' }}>
                <SuspenseLoadingPage debugName="UserInfo">
                    <Switch>
                        <Match when={tab() === 'info'}>
                            <UserDetailsTab initialType={props.initialType} groups={props.groups} />
                        </Match>
                        <Match when={tab() === 'selections' && props.user}>
                            {user => (
                                <StudentSelectionsTab
                                    userId={user().id}
                                    fallback={
                                        <p class="text-surface-variant text-center">
                                            {string.NO_X_YET({ object: string.SELECTIONS().toLowerCase() })}
                                        </p>
                                    }
                                />
                            )}
                        </Match>
                        <Match when={tab() === 'subjects' && props.user}>
                            {user => (
                                <TeacherSubjectsTab
                                    userId={user().id}
                                    fallback={
                                        <p class="text-surface-variant text-center">
                                            {string.NO_X_YET({ object: string.SUBJECTS().toLowerCase() })}
                                        </p>
                                    }
                                />
                            )}
                        </Match>
                    </Switch>
                </SuspenseLoadingPage>
            </VStack>

            <UserBottomActions />

            <Show when={props.extraActions}>
                {/* @ts-expect-error: Incorrect types */}
                <props.extraActions />
            </Show>
        </UserInfoContext.Provider>
    )
}
