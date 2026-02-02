import MinusCircleIcon from '@iconify-icons/mdi/minus-circle'
import { useRouter } from '@tanstack/solid-router'
import { Icon } from 'm3-solid'
import { createEffect } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { VStack } from '../Stack'
import type { Subject } from '../../api'

export default function UnenrollDialog(props: {
    open: boolean
    onClose: () => unknown
    electiveId: number
    selectedSubject?: Subject
}) {
    const api = useAPI()
    const router = useRouter()
    const { string, t } = useI18n()

    createEffect(() => {
        // Close the dialog once the state is invalidated
        if (!props.selectedSubject && props.open) {
            props.onClose()
        }
    })

    return (
        <Dialog
            closedBy="any"
            onClose={props.onClose}
            open={props.open}
            headline={
                <VStack gap={16}>
                    <h1 class="m3-headline-small">{string.ENROLLMENT_CANCEL()}</h1>
                    <p class="m3-body-medium text-ws-pre-line">
                        {t('ENROLLMENT_CANCEL_DESCRIPTION', {
                            subjectName: props.selectedSubject?.name ?? '???',
                        })}
                    </p>
                </VStack>
            }
            icon={<Icon fill="var(--m3c-secondary)" icon={MinusCircleIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }}>
                    <Button variant="text" onClick={() => props.onClose()}>
                        {string.CANCEL()}
                    </Button>
                    <Button
                        variant="tonal-error"
                        onClick={async () => {
                            await api.client.selections.delete('@me', props.electiveId)

                            props.onClose()

                            await router.invalidate({
                                sync: true,
                            })
                        }}
                    >
                        {string.UNENROLL()}
                    </Button>
                </form>
            }
        />
    )
}
