import { createContext, createRenderEffect, useContext } from 'solid-js'
import { createStore } from 'solid-js/store'
import { nonNull } from '~/utils'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { VStack } from '../Stack'
import EnrollmentDetailsTab from './EnrollmentDetailsTab'
import type { Enrollment } from '~/api'

export interface EnrollmentInfoProps {
    enrollment: Enrollment
    editable?: boolean
    creating?: boolean
    onEdit?: (field: string, value: unknown) => Promise<void> | void
    onSave?: () => Promise<void> | void
    onDelete?: () => Promise<void> | void
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

    return (
        <EnrollmentInfoContext.Provider value={info}>
            <VStack gap={16} grow>
                <SuspenseLoadingPage debugName="EnrollmentInfo">
                    <EnrollmentDetailsTab />
                </SuspenseLoadingPage>
            </VStack>
        </EnrollmentInfoContext.Provider>
    )
}
