import { Show } from 'solid-js'
import { useI18n } from '~/providers/I18nProvider'
import { Option, Select } from './Select'
import type { Group } from '~/api'

export interface GroupSelectProps {
    required?: boolean
    label?: string
    placeholder?: string
    value: number | null
    groups: Group[]
    disabled?: boolean
    supportingText?: string
    onInput: (value: number | null) => void
}

export function GroupSelect(props: GroupSelectProps) {
    const { string } = useI18n()

    return (
        <Select
            disabled={props.disabled}
            label={props.label ?? string.GROUP()}
            value={props.value ?? ''}
            supportingText={props.supportingText}
            onInput={e => {
                const val = e.currentTarget.value
                const parsed = val === '' ? null : Number(val)
                props.onInput(parsed)
            }}
        >
            <Option value="" hidden={props.required} selected={props.value === null}>
                {props.placeholder ?? string.SELECT_GROUP_HINT()}
            </Option>
            <Show when={props.groups}>
                {g =>
                    g().map(group => (
                        <Option value={group.id} selected={group.id === props.value}>
                            {group.name}
                        </Option>
                    ))
                }
            </Show>
        </Select>
    )
}
