import AccountCircleIcon from '@iconify-icons/mdi/account-circle-outline'
import HashTagBoxOutlineIcon from '@iconify-icons/mdi/hashtag-box-outline'
import LabelOutlineIcon from '@iconify-icons/mdi/label-outline'
import LocationIcon from '@iconify-icons/mdi/location-on-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import PeopleIcon from '@iconify-icons/mdi/people-outline'
import TeachIcon from '@iconify-icons/mdi/teach'
import { mergeClasses } from 'm3-solid'
import { marked } from 'marked'
import { createSignal, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { type Subject, SubjectTag, User } from '../../api'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { type I18nApi, useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import { Button } from '../Button'
import SetSubjectTagDialog from '../dialogs/SetSubjectTagDialog'
import TextFieldDialog from '../dialogs/TextFieldDialog'
import IconLabel from '../IconLabel'
import { HStack, VStack } from '../Stack'
import styles from './SubjectDetailsTab.module.css'
import SubjectImage from './SubjectImage'
import { useSubjectInfoContext } from './SubjectInfo'
import type { PatchSetterKey } from './SubjectDisplayContext'

interface SubjectDetailsTabProps {
    imageClass?: string
    imagePlaceholderClass?: string
    descriptionClass?: string
    labelClass?: string
    thumbnailClass?: string
}

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
    teamId: {
        getValue: s => s.teamId,
        parse: (v, s) => {
            if (v.trim() === '') return [null, undefined]
            const n = Number(v)
            if (Number.isNaN(n) || n < 0) return [undefined, s.ERROR_NUMERIC_VALUE({ field: s.TEAM_ID() })]
            return [n]
        },
        name: s => s.TEAM_ID(),
        required: false,
        patchKey: 'patchTeamId',
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

export default function SubjectDetailsTab(props: SubjectDetailsTabProps) {
    const { string } = useI18n()
    const enrollmentCounts = useEnrollmentCounts()
    const ctx = useSubjectInfoContext()

    const subject = () => nonNull(ctx.subject)

    const enrolledCount = () => {
        if (ctx.elective && ctx.subject) return enrollmentCounts.getCount(ctx.elective.id, ctx.subject.id)
    }

    const [activeField, setActiveField] = createSignal<ActiveField | null>(null)
    const [tagDialogOpen, setTagDialogOpen] = createSignal(false)

    const descriptionInnerHtml = () => marked(subject().description ?? '') as string
    const teachersText = () => {
        const teachers =
            subject()
                .teachers.map(t => new User(t).fullName)
                .join(', ') || '-'
        return `${string.TEACHERS()}: ${teachers}`
    }

    const categoryName = () => {
        const tag = subject().tag
        const key = SubjectTag[tag]
        if (!key || tag === SubjectTag.UNRECOGNIZED) return `${string.SUBJECT_CATEGORY_OTHER()}*`
        // @ts-expect-error: Dynamic key
        return string[`SUBJECT_CATEGORY_${key}`]()
    }

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
            initialValue: String(def.getValue(subject()) ?? ''),
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

    const EditButton = (p: { field: string }) => (
        <Show when={ctx.editable && ctx.onEdit}>
            <Button
                size="xs"
                variant="text"
                icon={PencilOutlineIcon}
                iconType="only"
                onClick={() => startEditing(p.field)}
                style={{ height: '32px', width: '32px', 'min-width': '32px' }}
            />
        </Show>
    )

    return (
        <Show when={ctx.subject}>
            <div style={{ position: 'relative' }}>
                <SubjectImage
                    imageUrl={subject().imageUrl}
                    class={props.imageClass}
                    placeholderClass={props.imagePlaceholderClass}
                />
                <Show when={subject().thumbnailUrl}>
                    <div style={{ position: 'absolute', bottom: '8px', left: '8px' }}>
                        <SubjectImage
                            imageUrl={subject().thumbnailUrl}
                            class={props.thumbnailClass}
                            placeholderClass={props.imagePlaceholderClass}
                        />
                    </div>
                </Show>
                <Show when={ctx.editable && ctx.onEdit}>
                    <HStack style={{ position: 'absolute', top: '8px', right: '8px' }} gap={8}>
                        <Button
                            size="xs"
                            variant="tonal"
                            icon={PencilOutlineIcon}
                            onClick={() => startEditing('thumbnailUrl')}
                        >
                            {string.THUMBNAIL_URL()}
                        </Button>
                        <Button
                            size="xs"
                            variant="tonal"
                            icon={PencilOutlineIcon}
                            onClick={() => startEditing('imageUrl')}
                        >
                            {string.IMAGE_URL()}
                        </Button>
                    </HStack>
                </Show>
            </div>
            <VStack gap={4}>
                <HStack alignVertical="center" gap={4}>
                    <h1 class="m3-headline-medium">{subject().name}</h1>
                    <EditButton field="name" />
                </HStack>
                <VStack gap={0} style={{ color: 'var(--m3c-on-surface-variant)' }}>
                    <HStack class={styles.infoRow}>
                        <HStack alignVertical="center" gap={4}>
                            <IconLabel
                                icon={LabelOutlineIcon}
                                text={categoryName()}
                                class={mergeClasses(
                                    props.labelClass,
                                    subject().tag === SubjectTag.UNRECOGNIZED && 'text-error',
                                )}
                            />
                            <Show when={ctx.editable && ctx.onEdit}>
                                <Button
                                    size="xs"
                                    variant="text"
                                    icon={PencilOutlineIcon}
                                    iconType="only"
                                    onClick={() => setTagDialogOpen(true)}
                                    style={{ height: '32px', width: '32px', 'min-width': '32px' }}
                                />
                            </Show>
                        </HStack>
                        <HStack alignVertical="center" gap={4}>
                            <IconLabel icon={HashTagBoxOutlineIcon} text={subject().code} class={props.labelClass} />
                            <EditButton field="code" />
                        </HStack>
                        <HStack alignVertical="center" gap={4}>
                            <IconLabel icon={LocationIcon} text={subject().location} class={props.labelClass} />
                            <EditButton field="location" />
                        </HStack>
                    </HStack>
                    <HStack class={styles.infoRow}>
                        <IconLabel icon={TeachIcon} text={teachersText()} class={props.labelClass} />
                        <HStack alignVertical="center" gap={4}>
                            <Show
                                when={enrolledCount() !== undefined}
                                fallback={
                                    <IconLabel
                                        icon={AccountCircleIcon}
                                        text={string.SUBJECT_CAPACITY_COUNT({
                                            count: subject().capacity,
                                        })}
                                        class={props.labelClass}
                                    />
                                }
                            >
                                <IconLabel
                                    icon={AccountCircleIcon}
                                    text={string.SUBJECT_MEMBERS_COUNT({
                                        count: nonNull(enrolledCount()),
                                        total: subject().capacity,
                                    })}
                                    class={props.labelClass}
                                />
                            </Show>
                            <EditButton field="capacity" />
                        </HStack>
                    </HStack>
                    <Show when={ctx.editable}>
                        <HStack alignVertical="center" gap={4}>
                            <IconLabel
                                icon={PeopleIcon}
                                text={`${string.TEAM_ID()}: ${subject().teamId ?? '-'}`}
                                class={props.labelClass}
                            />
                            <EditButton field="teamId" />
                        </HStack>
                    </Show>
                </VStack>
            </VStack>
            <HStack alignVertical="start" gap={4} grow alignHorizontal="center">
                <p class={props.descriptionClass} innerHTML={descriptionInnerHtml()} />
                <EditButton field="description" />
            </HStack>

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

            <Portal>
                <SetSubjectTagDialog
                    open={tagDialogOpen()}
                    onClose={() => setTagDialogOpen(false)}
                    initialValue={subject().tag}
                    onSave={tag => ctx.onEdit?.('tag', tag)}
                />
            </Portal>
        </Show>
    )
}
