import { mergeClasses } from 'm3-solid'
import { type JSX, splitProps } from 'solid-js'
import styles from './Select.module.css'

export interface SelectProps extends JSX.SelectHTMLAttributes<HTMLSelectElement> {
    label?: string
    error?: boolean
    supportingText?: string
}

export function Select(props: SelectProps) {
    const [local, others] = splitProps(props, ['label', 'error', 'supportingText', 'class', 'value', 'children'])
    const generatedId = `select-${Math.random().toString(36).slice(2)}`

    return (
        <div class={`${styles.container} ${local.class || ''}`}>
            {local.label && (
                <label class={styles.label} for={generatedId}>
                    {local.label}
                </label>
            )}
            <select
                id={generatedId}
                class={`${styles.select} ${local.error ? styles.error : ''}`}
                {...others}
                value={local.value ?? ''}
            >
                {local.children}
            </select>
            {local.supportingText && (
                <span class={`${styles.supportingText} ${local.error ? styles.errorText : ''}`}>
                    {local.supportingText}
                </span>
            )}
        </div>
    )
}

export function Option(props: JSX.OptionHTMLAttributes<HTMLOptionElement>) {
    return (
        <option {...props} class={mergeClasses(styles.option, 'm3-ripple', props.class)}>
            {props.children}
        </option>
    )
}
