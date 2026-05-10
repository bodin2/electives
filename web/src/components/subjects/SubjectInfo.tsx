import {
    type Component,
    createContext,
    createRenderEffect,
    createSignal,
    Match,
    Show,
    Switch,
    useContext,
} from 'solid-js'
import { createStore } from 'solid-js/store'
import { useTabPersistence } from '../../hooks/useTabPersistence'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { VStack } from '../Stack'
import StickyTabs from '../StickyTabs'
import SubjectBottomActions from './SubjectBottomActions'
import SubjectDetailsTab from './SubjectDetailsTab'
import styles from './SubjectInfo.module.css'
import SubjectMembersTab from './SubjectMembersTab'
import type { Enrollment, Subject, User } from '../../api'
import type { PatchSetterKey } from './SubjectDisplayContext'

export interface SubjectInfoProps {
    subject: Subject
    enrollment?: Enrollment
    user?: User
    editable?: boolean
    onEdit?: (field: string, value: any, patchKey?: PatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onStudentRemove?: (student: User) => Promise<void> | void
    studentRemoveDisabled?: boolean
    onTeacherRemove?: (teacher: User) => Promise<void> | void
    creating?: boolean
    extraActions?: Component<{ subject: Subject; enrollment?: Enrollment }>
    persistTab?: boolean
    teachers?: User[]
}

interface SubjectInfoContext {
    subject: Subject
    enrollment?: Enrollment
    user?: User
    editable?: boolean
    onEdit?: (field: string, value: any, patchKey?: PatchSetterKey) => Promise<void> | void
    onSave?: () => Promise<void> | void
    teachers?: User[]
}

const SubjectInfoContext = createContext<SubjectInfoContext>(null as unknown as SubjectInfoContext)
export const useSubjectInfoContext = () =>
    nonNull(useContext(SubjectInfoContext), 'useSubjectInfoContext must be used within a SubjectInfo provider')

export default function SubjectInfo(props: SubjectInfoProps) {
    const { string } = useI18n()

    const [tab, setTab] = createSignal('info')
    useTabPersistence(tab, setTab, { disabled: props.persistTab === false })

    // SolidJS moment
    const [info, setInfo] = createStore<SubjectInfoContext>(null as unknown as SubjectInfoContext)
    createRenderEffect(() => {
        setInfo({
            subject: props.subject,
            enrollment: props.enrollment,
            user: props.user,
            editable: props.editable,
            onEdit: props.onEdit,
            onSave: props.onSave,
            teachers: props.teachers,
        })
    })

    return (
        <SubjectInfoContext.Provider value={info}>
            <Show when={!props.creating}>
                <StickyTabs
                    value={tab()}
                    onChange={setTab}
                    tabs={[
                        { label: string.SUBJECT(), value: 'info' },
                        { label: string.MEMBERS_LIST(), value: 'members' },
                    ]}
                />
            </Show>
            <VStack gap={16} grow class={`padded ${styles.tabContent}`}>
                <Switch>
                    <Match when={tab() === 'info'}>
                        <SuspenseLoadingPage debugName="SubjectDetails">
                            <SubjectDetailsTab />
                        </SuspenseLoadingPage>
                    </Match>
                    <Match when={tab() === 'members'}>
                        <SuspenseLoadingPage debugName="SubjectMembers">
                            <SubjectMembersTab
                                onStudentRemove={props.onStudentRemove}
                                studentRemoveDisabled={props.studentRemoveDisabled}
                                onTeacherRemove={props.onTeacherRemove}
                            />
                        </SuspenseLoadingPage>
                    </Match>
                </Switch>
            </VStack>

            <SubjectBottomActions>
                {props.extraActions
                    ? nonNull(props.extraActions)({ subject: props.subject, enrollment: props.enrollment })
                    : undefined}
            </SubjectBottomActions>
        </SubjectInfoContext.Provider>
    )
}
