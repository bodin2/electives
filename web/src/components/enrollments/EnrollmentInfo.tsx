import { createContext, createRenderEffect, createSignal, Match, Show, Switch, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'
import { useTabPersistence } from '~/hooks/useTabPersistence'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { VStack } from '../Stack'
import StickyTabs from '../StickyTabs'
import EnrollmentDetailsTab from './EnrollmentDetailsTab'
import EnrollmentUnenrolledTab from './EnrollmentUnenrolledTab'
import type { Enrollment } from '~/api'

export interface EnrollmentInfoProps {
    enrollment: Enrollment
    editable?: boolean
    creating?: boolean
    onEdit?: (field: string, value: unknown) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
    persistTab?: boolean
}

interface EnrollmentInfoContext {
    enrollment: Enrollment
    editable?: boolean
    creating?: boolean
    onEdit?: (field: string, value: unknown, patchKey?: string) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
}

const EnrollmentInfoContext = createContext<EnrollmentInfoContext>(null as unknown as EnrollmentInfoContext)
export const useEnrollmentInfoContext = () =>
    nonNull(
        useContext(EnrollmentInfoContext),
        'useEnrollmentInfoContext must be used within an EnrollmentInfo provider',
    )

export default function EnrollmentInfo(props: EnrollmentInfoProps) {
    const { string } = useI18n()

    const [tab, setTab] = createSignal('details')
    useTabPersistence(tab, setTab, { disabled: props.persistTab === false })

    const [info, setInfo] = createStore<EnrollmentInfoContext>(null as unknown as EnrollmentInfoContext)
    createRenderEffect(() => {
        setInfo({
            enrollment: props.enrollment,
            editable: props.editable,
            onEdit: props.onEdit,
            onSave: props.onSave,
            onDelete: props.onDelete,
            creating: props.creating,
        })
    })

    const tabs = () =>
        [
            { label: string.ENROLLMENT(), value: 'details' },
            !props.creating && { label: string.UNASSIGNED_STUDENTS(), value: 'unassigned' },
        ].filter(x => !!x)

    return (
        <EnrollmentInfoContext.Provider value={info}>
            <Show when={tabs().length > 1}>
                <StickyTabs value={tab()} onChange={setTab} tabs={tabs()} />
            </Show>
            <VStack gap={16} grow>
                <SuspenseLoadingPage debugName="EnrollmentInfo">
                    <Switch>
                        <Match when={tab() === 'details'}>
                            <EnrollmentDetailsTab stickyOffset={tabs().length > 1 ? 48 : 0} />
                        </Match>
                        <Match when={tab() === 'unassigned'}>
                            <EnrollmentUnenrolledTab />
                        </Match>
                    </Switch>
                </SuspenseLoadingPage>
            </VStack>
        </EnrollmentInfoContext.Provider>
    )
}
