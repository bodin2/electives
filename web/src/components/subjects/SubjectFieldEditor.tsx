import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { createSignal, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { type Subject, SubjectTag } from '../../api'
import { type I18nApi, useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { Button } from '../Button'
import TextFieldDialog from '../dialogs/base/TextFieldDialog'
import { useSubjectInfoContext } from './SubjectInfo'
import type { PatchSetterKey } from './SubjectDisplayContext'

interface Field<T> {
    getValue: (subject: Subject) => T | undefined
    parse?: (value: any, string: I18nApi['string']) => [value: T, error?: undefined] | [value: undefined, error: string]
    name: (string: I18nApi['string']) => string
    required: boolean
    multiline?: boolean
    patchKey?: PatchSetterKey
}

const FIELDS: Record<string, Field<unknown>> = {
    id: {
        getValue: s => s.id,
        parse: (v, s) => {
            const n = Number(v)
            if (Number.isNaN(n) || n < 0) return [undefined, s.ERROR_NUMERIC_VALUE({ field: s.USER_ID() })]
            return [n]
        },
        name: s => s.USER_ID(),
        required: true,
    },
    name: {
        getValue: s => s.name,
        parse: (v, s) => (!v.trim() ? [undefined, s.ERROR_REQUIRED_FIELD_GENERIC()] : [v.trim(), undefined]),
        name: s => s.NAME(),
        required: true,
    },
    description: {
        getValue: s => s.description,
        parse: v => [v, undefined],
        name: s => s.DESCRIPTION(),
        multiline: true,
        required: false,
        patchKey: 'patchDescription',
    },
    code: {
        getValue: s => s.code,
        parse: (v, s) => (!v.trim() ? [undefined, s.ERROR_REQUIRED_FIELD_GENERIC()] : [v.trim(), undefined]),
        name: s => s.CODE(),
        required: true,
        patchKey: 'patchCode',
    },
    location: {
        getValue: s => s.location,
        parse: (v, s) => (!v.trim() ? [undefined, s.ERROR_REQUIRED_FIELD_GENERIC()] : [v.trim(), undefined]),
        name: s => s.LOCATION(),
        required: true,
        patchKey: 'patchLocation',
    },
    capacity: {
        getValue: s => s.capacity,
        parse: (v, s) => {
            if (v.trim() === '') return [undefined, s.ERROR_REQUIRED_FIELD_GENERIC()]
            const n = Number(v)
            if (Number.isNaN(n)) return [undefined, s.ERROR_NUMERIC_VALUE({ field: s.CAPACITY() })]
            return [n]
        },
        name: s => s.CAPACITY(),
        required: true,
    },
    thumbnailUrl: {
        getValue: s => s.thumbnailUrl,
        name: s => s.THUMBNAIL_URL(),
        required: false,
        patchKey: 'patchThumbnailUrl',
    },
    imageUrl: {
        getValue: s => s.imageUrl,
        name: s => s.IMAGE_URL(),
        required: false,
        patchKey: 'patchImageUrl',
    },
    tag: {
        getValue: s => s.tag,
        parse: (v, s) => (v === SubjectTag.UNRECOGNIZED ? [undefined, s.ERROR_SELECT_CATEGORY()] : [v, undefined]),
        name: s => s.CATEGORY(),
        required: true,
    },
}

interface ActiveField {
    key: string
    label: string
    initialValue: string
    required: boolean
    multiline?: boolean
    parser?: (value: any, string: I18nApi['string']) => [value: any, error?: string]
    patchKey?: PatchSetterKey
}

/**
 * Hook that manages the field editing state and provides helpers for inline editing.
 * Returns `EditButton` (a component) and `startEditing` (to trigger programmatic editing).
 * Renders the `TextFieldDialog` portal internally.
 */
export function useFieldEditor() {
    const { string } = useI18n()
    const ctx = useSubjectInfoContext()
    const [activeField, setActiveField] = createSignal<ActiveField | null>(null)

    const startEditing = (key: string) => {
        const def = FIELDS[key]
        let label = def.name(string)
        if (!def.required) {
            label += ` ${string.IF_ANY()}`
        }

        setActiveField({
            key,
            label,
            required: def.required,
            initialValue: String(def.getValue(ctx.subject) ?? ''),
            multiline: def.multiline,
            parser: def.parse,
            patchKey: def.patchKey,
        })
    }

    const onSave = async (value: string | null) => {
        const active = activeField()
        if (!active) return

        const { key } = active
        const [parsed, error] = active.parser ? active.parser(value, string) : [value, undefined]
        if (error) return

        await ctx.onEdit?.(key, parsed, active.patchKey)
        setActiveField(null)
    }

    const EditButton = (p: { field?: string; onClick?: () => void }) => (
        <Show when={ctx.editable && ctx.onEdit}>
            <Button
                size="xs"
                variant="text"
                icon={PencilOutlineIcon}
                iconType="only"
                onClick={p.onClick ?? (() => startEditing(nonNull(p.field)))}
                style={{ height: '32px', width: '32px', 'min-width': '32px' }}
            />
        </Show>
    )

    const FieldEditorDialog = () => (
        <Show when={activeField()}>
            {field => (
                <Portal>
                    <TextFieldDialog
                        open
                        multiline={field().multiline}
                        dialog={{ quick: true }}
                        required={field().required}
                        onClose={() => setActiveField(null)}
                        onSave={onSave}
                        label={field().label}
                        initialValue={field().initialValue}
                        validator={
                            field().parser &&
                            ((val: string) => {
                                const [, error] = nonNull(field().parser)(val, string)
                                return error ?? null
                            })
                        }
                    />
                </Portal>
            )}
        </Show>
    )

    return { EditButton, startEditing, FieldEditorDialog }
}
