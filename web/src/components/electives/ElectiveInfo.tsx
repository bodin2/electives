import { createContext, createEffect, createSignal, Match, Show, Switch, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'
import { useTabPersistence } from '../../hooks/useTabPersistence'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { VStack } from '../Stack'
import StickyTabs from '../StickyTabs'
import ElectiveDetailsTab from './ElectiveDetailsTab'
import ElectiveUnenrolledTab from './ElectiveUnenrolledTab'
import type { Elective } from '../../api'

export interface ElectiveInfoProps {
    elective: Elective
    editable?: boolean
    creating?: boolean
    onEdit?: (field: string, value: unknown) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
    persistTab?: boolean
}

interface ElectiveInfoContext {
    elective: Elective
    editable?: boolean
    creating?: boolean
    onEdit?: (field: string, value: unknown, patchKey?: string) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
}

const ElectiveInfoContext = createContext<ElectiveInfoContext>(null as unknown as ElectiveInfoContext)
export const useElectiveInfoContext = () =>
    nonNull(useContext(ElectiveInfoContext), 'useElectiveInfoContext must be used within an ElectiveInfo provider')

export default function ElectiveInfo(props: ElectiveInfoProps) {
    const { string } = useI18n()

    const [tab, setTab] = createSignal('details')
    useTabPersistence(tab, setTab, { disabled: props.persistTab === false })

    const [info, setInfo] = createStore<ElectiveInfoContext>(null as unknown as ElectiveInfoContext)
    createEffect(() => {
        setInfo({
            elective: props.elective,
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
        <ElectiveInfoContext.Provider value={info}>
            <Show when={tabs().length > 1}>
                <StickyTabs value={tab()} onChange={setTab} tabs={tabs()} />
            </Show>
            <VStack gap={16} grow>
                <SuspenseLoadingPage>
                    <Switch>
                        <Match when={tab() === 'details'}>
                            <ElectiveDetailsTab stickyOffset={tabs().length > 1 ? 48 : 0} />
                        </Match>
                        <Match when={tab() === 'unassigned'}>
                            <ElectiveUnenrolledTab />
                        </Match>
                    </Switch>
                </SuspenseLoadingPage>
            </VStack>
        </ElectiveInfoContext.Provider>
    )
}
