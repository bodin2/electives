import MinusCircleIcon from '@iconify-icons/mdi/minus-circle'
import { Icon } from 'm3-solid'
import { createEffect } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { VStack } from '../Stack'
import { ConfirmDialog } from './base/ConfirmDialog'
import type { Subject } from '../../api'

export default function UnenrollDialog(props: {
    open: boolean
    onClose: (removed?: boolean) => unknown
    electiveId: number
    selectedSubject?: Subject
}) {
    const api = useAPI()
    const { string } = useI18n()

    createEffect(() => {
        // Close the dialog once the state is invalidated
        if (!props.selectedSubject && props.open) {
            props.onClose(true)
        }
    })

    return (
        <ConfirmDialog
            variant="danger"
            closedBy="any"
            onCancel={() => props.onClose(false)}
            onConfirm={async () => {
                await api.client.selections.delete('@me', props.electiveId)
                props.onClose(true)
            }}
            confirmText={string.UNENROLL()}
            open={props.open}
            headline={
                <VStack gap={16}>
                    <h1 class="m3-headline-small">{string.ENROLLMENT_CANCEL()}</h1>
                    <p class="m3-body-medium text-ws-pre-line">
                        {string.ENROLLMENT_CANCEL_DESCRIPTION({
                            subjectName: <strong>{props.selectedSubject?.name ?? ''}</strong>,
                        })}
                    </p>
                </VStack>
            }
            icon={<Icon fill="var(--m3c-secondary)" icon={MinusCircleIcon} />}
            centerHeadline
        />
    )
}
