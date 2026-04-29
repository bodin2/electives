import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import MinusCircleIcon from '@iconify-icons/mdi/minus-circle'
import { createMemo, createSignal, Match, Show, Switch } from 'solid-js'
import { Portal } from 'solid-js/web'
import useElectiveOpen from '../../hooks/useElectiveOpen'
import useSubjectFull from '../../hooks/useSubjectFull'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { formatCountdown } from '../../utils/date'
import { Button } from '../Button'
import SubjectEnrollmentFailedDialog from '../dialogs/SubjectEnrollmentFailedDialog'
import UnenrollDialog from '../dialogs/UnenrollDialog'
import { VStack } from '../Stack'
import type { Elective, Subject } from '../../api'

export default function DynamicEnrollButton(props: {
    elective: Elective
    subject: Subject
    selectedSubject: Subject | undefined
    class?: string
    onInvalidate: () => Promise<void> | void
}) {
    const api = useAPI()
    const { string } = useI18n()
    const [error, setError] = createSignal<string | null>(null)

    const [dialogOpen, setDialogOpen] = createSignal(false)
    const [countdown, setCountdown] = createSignal<number | null>(null)

    const electiveOpen = useElectiveOpen(props.elective, {
        onCountdown: timeRemaining => setCountdown(timeRemaining),
    })

    const isFull = useSubjectFull(
        () => props.subject,
        () => props.elective,
    )

    const enrollState = () => {
        if (!props.selectedSubject) {
            return EnrollState.NotEnrolled
        }

        if (props.selectedSubject.id === props.subject.id) {
            return EnrollState.EnrolledCurrent
        }

        return EnrollState.Enrolled
    }

    const buttonProps = createMemo(() => {
        switch (enrollState()) {
            case EnrollState.NotEnrolled:
                return {
                    variant: 'filled',
                    children: string.ENROLL(),
                    icon: AddCircleIcon,
                } as const
            case EnrollState.Enrolled:
                return {
                    variant: 'tonal-error',
                    children: string.UNENROLL_OTHER_SUBJECT(),
                    icon: MinusCircleIcon,
                } as const
            case EnrollState.EnrolledCurrent:
                return {
                    variant: 'tonal-error',
                    children: string.UNENROLL(),
                    icon: MinusCircleIcon,
                } as const
        }
    })

    return (
        <VStack alignHorizontal="center">
            <Button
                class={props.class}
                {...buttonProps()}
                disabled={!electiveOpen() || (enrollState() !== EnrollState.EnrolledCurrent && isFull())}
                size="m"
                onClick={async () => {
                    switch (enrollState()) {
                        case EnrollState.NotEnrolled:
                            try {
                                await api.client.selections.set('@me', props.elective.id, props.subject.id)
                                await props.onInvalidate()
                            } catch (e) {
                                setError(String(e))
                            }
                            break

                        case EnrollState.Enrolled:
                        case EnrollState.EnrolledCurrent:
                            setDialogOpen(true)
                            return
                    }
                }}
            />
            <Switch>
                <Match when={!electiveOpen()}>
                    <Show
                        when={formatCountdown(countdown())}
                        fallback={<p class="m3-body-small text-error">{string.ENROLLMENT_CLOSED()}</p>}
                    >
                        {time => (
                            <p class="m3-body-small text-error">
                                {string.ENROLLMENT_CLOSED_OPENING_IN({ time: time() })}
                            </p>
                        )}
                    </Show>
                </Match>
                <Match when={enrollState() === EnrollState.Enrolled}>
                    <p class="m3-body-small text-center text-balance text-ws-pre-line">
                        {string.UNENROLL_OTHER_SUBJECT_HINT({ subjectName: props.selectedSubject?.name ?? '???' })}
                    </p>
                </Match>
            </Switch>
            <Portal>
                <UnenrollDialog
                    open={dialogOpen()}
                    onClose={async removed => {
                        setDialogOpen(false)
                        if (removed) await props.onInvalidate()
                    }}
                    electiveId={props.elective.id}
                    selectedSubject={props.selectedSubject}
                />
                <SubjectEnrollmentFailedDialog reason={error()} onClose={() => setError(null)} />
            </Portal>
        </VStack>
    )
}

const EnrollState = {
    Enrolled: 0,
    EnrolledCurrent: 1,
    NotEnrolled: 2,
} as const
