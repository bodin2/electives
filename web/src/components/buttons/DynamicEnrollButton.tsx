import AddCircleIcon from '@iconify-icons/mdi/add-circle'
import MinusCircleIcon from '@iconify-icons/mdi/minus-circle'
import { useRouter } from '@tanstack/solid-router'
import { createMemo, createSignal, Match, Switch } from 'solid-js'
import { Portal } from 'solid-js/web'
import useElectiveOpen from '../../hooks/useElectiveOpen'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import UnenrollDialog from '../dialogs/UnenrollDialog'
import type { Elective, Subject } from '../../api'

export default function DynamicEnrollButton(props: {
    elective: Elective
    subject: Subject
    selectedSubject: Subject | undefined
    class?: string
}) {
    const api = useAPI()
    const router = useRouter()
    const { string } = useI18n()
    const enrollment = useEnrollmentCounts()
    const electiveOpen = useElectiveOpen(props.elective)

    const [dialogOpen, setDialogOpen] = createSignal(false)

    const enrolledCount = () => enrollment.getElectiveCounts(props.elective.id)[props.subject.id] ?? 0
    const isFull = () => enrolledCount() >= props.subject.capacity

    const enrollState = createMemo(() => {
        if (!props.selectedSubject) {
            return EnrollState.NotEnrolled
        }

        if (props.selectedSubject.id === props.subject.id) {
            return EnrollState.EnrolledCurrent
        }

        return EnrollState.Enrolled
    })

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
        <>
            <Button
                class={props.class}
                {...buttonProps()}
                disabled={!electiveOpen() || (enrollState() !== EnrollState.EnrolledCurrent && isFull())}
                size="m"
                onClick={async () => {
                    switch (enrollState()) {
                        case EnrollState.NotEnrolled:
                            await api.client.selections.set('@me', props.elective.id, props.subject.id)
                            await router.invalidate({
                                sync: true,
                            })
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
                    <p class="m3-body-small text-error">{string.ELECTIVE_CLOSED_HINT()}</p>
                </Match>
                <Match when={isFull()}>
                    <p class="m3-body-small text-error">{string.SUBJECT_FULL_HINT()}</p>
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
                    onClose={() => setDialogOpen(false)}
                    electiveId={props.elective.id}
                    selectedSubject={props.selectedSubject}
                />
            </Portal>
        </>
    )
}

enum EnrollState {
    Enrolled = 0,
    EnrolledCurrent = 1,
    NotEnrolled = 2,
}
