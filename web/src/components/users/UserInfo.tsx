import { type Component, createSignal, Match, Show, Switch } from 'solid-js'
import { useTabPersistence } from '../../hooks/useTabPersistence'
import { useI18n } from '../../providers/I18nProvider'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { VStack } from '../Stack'
import StickyTabs from '../StickyTabs'
import StudentSelectionsTab from './StudentSelectionsTab'
import TeacherSubjectsTab from './TeacherSubjectsTab'
import UserBottomActions from './UserBottomActions'
import UserDetailsTab from './UserDetailsTab'
import { useUserDisplayContext } from './UserDisplayContext'
import styles from './UserInfo.module.css'
import type { Team, UserType } from '../../api'

export interface UserInfoProps {
    extraActions?: Component
    initialType?: UserType
    teams?: Team[]
    persistTab?: boolean
}

export default function UserInfo(props: UserInfoProps) {
    const { string } = useI18n()
    const ctx = useUserDisplayContext()

    const [tab, setTab] = createSignal('info')
    useTabPersistence(tab, setTab, { disabled: props.persistTab === false })

    const tabs = () => {
        const list = [{ label: string.USER_INFO(), value: 'info' }]
        if (!ctx.creating) {
            if (ctx.user?.isStudent()) {
                list.push({ label: string.SELECTIONS(), value: 'selections' })
            }

            if (ctx.user?.isTeacher()) {
                list.push({ label: string.SUBJECTS(), value: 'subjects' })
            }
        }
        return list
    }

    return (
        <>
            <Show when={ctx.user}>
                <Show when={tabs().length > 1}>
                    <StickyTabs value={tab()} onChange={setTab} class={styles.tabs} tabs={tabs()} />
                </Show>
            </Show>
            <VStack gap={16} grow class={`padded ${styles.tabContent}`}>
                <SuspenseLoadingPage debugName="UserInfo">
                    <Switch>
                        <Match when={tab() === 'info'}>
                            <UserDetailsTab
                                avatarClass={styles.avatar}
                                avatarPlaceholderClass={`${styles.avatar} ${styles.placeholder}`}
                                descriptionClass={`${styles.description} m3-body-large`}
                                labelClass={styles.labelSubText}
                                initialType={props.initialType}
                                teams={props.teams}
                            />
                        </Match>
                        <Match when={tab() === 'selections' && ctx.user}>
                            {user => <StudentSelectionsTab userId={user().id} />}
                        </Match>
                        <Match when={tab() === 'subjects' && ctx.user}>
                            {user => <TeacherSubjectsTab userId={user().id} />}
                        </Match>
                    </Switch>
                </SuspenseLoadingPage>
            </VStack>

            <UserBottomActions />

            <Show when={props.extraActions}>
                {/* @ts-expect-error: Incorrect types */}
                <props.extraActions />
            </Show>
        </>
    )
}
