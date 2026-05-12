import { TextField, TextFieldMultiline, type TextFieldProps } from 'm3-solid/src'
import { createEffect, createMemo, createSignal, Show } from 'solid-js'
import { Dynamic } from 'solid-js/web'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../../Button'
import { Dialog, type DialogProps } from '../../Dialog'
import { VStack } from '../../Stack'

export type TextFieldDialogProps = {
    open: boolean
    onClose: () => void
    onSave: (value: string | null) => Promise<void> | void
    validator?: (value: string) => boolean | string | null
    placeholder?: string
    initialValue?: string
    label?: string
    required?: boolean
    textField?: Omit<TextFieldProps, 'placeholder' | 'label' | 'value' | 'onInput' | 'error' | 'supportingText'>
    dialog?: Omit<DialogProps, 'open' | 'onClose' | 'actions' | 'children'>
    multiline?: boolean
}

export default function TextFieldDialog(props: TextFieldDialogProps) {
    const { string } = useI18n()
    const [value, setValue] = createSignal(props.initialValue ?? '')
    const [error, setError] = createSignal<string | boolean | null>(null)

    createEffect(() => {
        if (props.open) {
            const initial = props.initialValue ?? ''
            setValue(initial)
            if (props.validator) {
                setError(props.validator(initial))
            } else {
                setError(null)
            }
        }
    })

    const handleInput = (e: InputEvent & { currentTarget: HTMLInputElement }) => {
        const newValue = e.currentTarget.value
        setValue(newValue)
        if (props.validator) {
            setError(props.validator(newValue))
        }
    }

    const isInvalid = createMemo(() => {
        const err = error()
        return err === true || typeof err === 'string'
    })

    const errorMessage = createMemo(() => {
        const err = error()
        return typeof err === 'string' ? err : undefined
    })

    const textFieldProps = () => ({
        autofocus: true,
        errorIcon: true,
        label: props.label,
        placeholder: props.placeholder,
        value: value(),
        onInput: handleInput,
        error: isInvalid() || undefined,
        supportingText: errorMessage(),

        ...props.textField,
    })

    let submitBtn!: HTMLButtonElement

    return (
        <Dialog
            open={props.open}
            onClose={props.onClose}
            actions={
                <form method="dialog" style={{ display: 'contents' }}>
                    <Show when={!props.required}>
                        <Button
                            variant="tonal-error"
                            onClick={async () => {
                                await props.onSave(null)
                                props.onClose()
                            }}
                        >
                            {string.RESET()}
                        </Button>
                    </Show>
                    <Button variant="text" onClick={props.onClose}>
                        {string.CANCEL()}
                    </Button>
                    <Button
                        ref={submitBtn}
                        variant="text"
                        disabled={isInvalid()}
                        onClick={async () => {
                            await props.onSave(value())
                            props.onClose()
                        }}
                    >
                        {string.SAVE()}
                    </Button>
                </form>
            }
            {...props.dialog}
        >
            <VStack
                as="form"
                onSubmit={e => {
                    e.preventDefault()
                    if (!isInvalid()) {
                        submitBtn.click()
                    }
                }}
            >
                <Dynamic component={props.multiline ? TextFieldMultiline : TextField} {...textFieldProps()} />
            </VStack>
        </Dialog>
    )
}
