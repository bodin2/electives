import ArrowDownIcon from '@iconify-icons/mdi/arrow-down'
import { mergeRefs } from '@solid-primitives/refs'
import { Icon, TextField } from 'm3-solid'
import { createSignal, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { HStack, VStack } from '../Stack'

export interface EnrollmentDateRange {
    start: [string, string]
    end: [string, string]
}

export function EnrollmentDatePicker(props: {
    setOpen: (open: boolean) => void
    open: boolean
    onSave: (value: EnrollmentDateRange) => void
    startDate: Date | undefined
    endDate: Date | undefined
}) {
    const { string } = useI18n()

    let form!: HTMLFormElement

    return (
        <Show when={props.open}>
            <Portal>
                <Dialog
                    quick
                    open
                    onClose={() => props.setOpen(false)}
                    headline={<h1 class="m3-headline-small">{string.ENROLLMENT_DATES()}</h1>}
                    actions={
                        <>
                            <Button variant="text" onClick={() => props.setOpen(false)}>
                                {string.CANCEL()}
                            </Button>
                            <Button variant="text" onClick={() => form.requestSubmit()}>
                                {string.SAVE()}
                            </Button>
                        </>
                    }
                >
                    <EnrollmentDatePickerForm
                        ref={form}
                        mode="center"
                        onSave={value => {
                            props.onSave(value)
                            props.setOpen(false)
                        }}
                        startDate={props.startDate}
                        endDate={props.endDate}
                    />
                </Dialog>
            </Portal>
        </Show>
    )
}

export function EnrollmentDatePickerForm(props: {
    onSave: (value: EnrollmentDateRange) => void
    ref?: HTMLFormElement
    mode: 'center' | 'stretch'
    startDate: Date | undefined
    endDate: Date | undefined
    saveOnUnfocus?: boolean
}) {
    const { string } = useI18n()

    const formatDate = (date: Date | undefined): [string, string] => {
        if (!date) return ['', '']
        const tzoffset = date.getTimezoneOffset() * 60000
        const localISOTime = new Date(date.getTime() - tzoffset).toISOString().slice(0, 16)
        return [localISOTime.split('T')[0], localISOTime.split('T')[1]]
    }

    const initialStart = formatDate(props.startDate)
    const initialEnd = formatDate(props.endDate)

    const [startDate, setStartDate] = createSignal(initialStart[0])
    const [startTime, setStartTime] = createSignal(initialStart[1])
    const [endDate, setEndDate] = createSignal(initialEnd[0])
    const [endTime, setEndTime] = createSignal(initialEnd[1])

    let form!: HTMLFormElement
    let startDateInput!: HTMLInputElement
    let startTimeInput!: HTMLInputElement
    let endDateInput!: HTMLInputElement
    let endTimeInput!: HTMLInputElement

    const getDateTime = (date: string, time: string) => {
        if (!date) return null
        return new Date(`${date}T${time || '00:00'}`)
    }

    let validating = false
    const validate = () => {
        if (validating) return
        validating = true
        const start = getDateTime(startDate(), startTime())
        const end = getDateTime(endDate(), endTime())
        const isInvalid = start !== null && end !== null && start >= end

        const startError = isInvalid ? string.ERROR_START_MUST_BE_BEFORE_END() : ''
        const endError = isInvalid ? string.ERROR_END_MUST_BE_AFTER_START() : ''

        startDateInput.setCustomValidity(startError)
        startTimeInput.setCustomValidity(startError)
        endDateInput.setCustomValidity(endError)
        endTimeInput.setCustomValidity(endError)

        validating = false
    }

    const handleBlur = () => {
        if (!props.saveOnUnfocus) return
        if (!startDate() || !startTime() || !endDate() || !endTime()) return
        const start = getDateTime(startDate(), startTime())
        const end = getDateTime(endDate(), endTime())
        if (!start || !end || start >= end) return
        props.onSave({
            start: [startDate(), startTime()],
            end: [endDate(), endTime()],
        })
    }

    return (
        <VStack
            ref={mergeRefs(form, props.ref)}
            gap={0}
            as="form"
            alignHorizontal={props.mode}
            style={{ '--m3-text-field-container-min-width': '12rem' }}
            onSubmit={e => {
                e.preventDefault()
                props.onSave({
                    start: [startDate(), startTime()],
                    end: [endDate(), endTime()],
                })
            }}
        >
            <VStack gap={16} grow>
                <h2 class="m3-label-large text-primary">{string.START_DATE()}</h2>
                <HStack gap={8} wrap>
                    <TextField
                        ref={startDateInput}
                        type="date"
                        label={string.DATE()}
                        value={startDate()}
                        onInput={e => {
                            setStartDate(e.currentTarget.value)
                            validate()
                        }}
                        onBlur={handleBlur}
                        containerStyle={{ flex: 1 }}
                    />
                    <TextField
                        ref={startTimeInput}
                        type="time"
                        label={string.TIME()}
                        value={startTime()}
                        onInput={e => {
                            setStartTime(e.currentTarget.value)
                            validate()
                        }}
                        onBlur={handleBlur}
                        containerStyle={{ flex: 1 }}
                    />
                </HStack>
            </VStack>
            <VStack style={{ 'margin-top': '24px', color: 'var(--m3c-outline)' }} grow alignHorizontal="center">
                <Icon icon={ArrowDownIcon} size={24} />
            </VStack>
            <VStack gap={16} grow>
                <h2 class="m3-label-large text-primary">{string.END_DATE()}</h2>
                <HStack gap={8} wrap>
                    <TextField
                        ref={endDateInput}
                        type="date"
                        label={string.DATE()}
                        value={endDate()}
                        onInput={e => {
                            setEndDate(e.currentTarget.value)
                            validate()
                        }}
                        onBlur={handleBlur}
                        containerStyle={{ flex: 1 }}
                    />
                    <TextField
                        ref={endTimeInput}
                        type="time"
                        label={string.TIME()}
                        value={endTime()}
                        onInput={e => {
                            setEndTime(e.currentTarget.value)
                            validate()
                        }}
                        onBlur={handleBlur}
                        containerStyle={{ flex: 1 }}
                    />
                </HStack>
            </VStack>
        </VStack>
    )
}
