import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import CalendarIcon from '@iconify-icons/mdi/calendar-clock-outline'
import SaveIcon from '@iconify-icons/mdi/content-save'
import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { createQuery } from '@tanstack/solid-query'
import { Icon } from 'm3-solid/src'
import { createEffect, createSignal, onCleanup, onMount, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useAPI } from '~/providers/APIProvider'
import { type I18nApi, useI18n } from '~/providers/I18nProvider'
import { groupsQueryOptions } from '~/queries/groups'
import { nonNull } from '~/utils'
import { formatDuration } from '~/utils/date'
import { GroupBadge } from '../Badges'
import BottomBar from '../BottomBar'
import { Button } from '../Button'
import TextFieldDialog from '../dialogs/base/TextFieldDialog'
import { SelectGroupDialog } from '../dialogs/SelectGroupDialog'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { HStack, VStack } from '../Stack'
import { EnrollmentDatePicker, EnrollmentDatePickerForm, type EnrollmentDateRange } from './EnrollmentDatePicker'
import { useEnrollmentInfoContext } from './EnrollmentInfo'
import EnrollmentSubjectsTab from './EnrollmentSubjectsTab'

interface ActiveField {
    key: string
    label: string
    initialValue: string
    required: boolean
    parser?: (value: any, string: I18nApi['string']) => [value: any, error?: string]
    patchKey?: string
}

const FIELDS: Record<string, ActiveField> = {
    name: {
        key: 'name',
        label: 'Name',
        initialValue: '',
        required: true,
        parser: (v, s) => (!v.trim() ? [undefined, s.ERROR_REQUIRED_FIELD_GENERIC()] : [v.trim(), undefined]),
        patchKey: 'patchName',
    },
}

export default function EnrollmentDetailsTab(props: { stickyOffset?: number }) {
    const { string, locale } = useI18n()
    const { client } = useAPI()
    const ctx = useEnrollmentInfoContext()

    const enrollment = () => nonNull(ctx.enrollment)
    const hasGroup = () => enrollment().groupId !== null

    // Default size = 152px
    const [dynamicStickyOffset, setDynamicStickyOffset] = createSignal(152 + (props.stickyOffset ?? 0))

    const [activeField, setActiveField] = createSignal<ActiveField | null>(null)
    const groupsQuery = createQuery(() => groupsQueryOptions(client))
    const [groupDialogOpen, setGroupDialogOpen] = createSignal(false)

    const [dateDialogOpen, setDateDialogOpen] = createSignal(false)

    const startDateStr = () =>
        enrollment().startDate ? formatDuration(locale, nonNull(enrollment().startDate)) : string.NOT_SET()
    const endDateStr = () =>
        enrollment().endDate ? formatDuration(locale, nonNull(enrollment().endDate)) : string.NOT_SET()

    const startEditing = (key: string) => {
        const def = FIELDS[key]
        let label = string.NAME()
        if (!def.required) {
            label += ` ${string.IF_ANY()}`
        }

        setActiveField({
            ...def,
            label,
            initialValue: enrollment().name,
        })
    }

    const isValid = () => {
        return ctx.enrollment.name && ctx.enrollment.startDate && ctx.enrollment.endDate
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

    const startDateEditing = () => {
        setDateDialogOpen(true)
    }

    const onDateSave = async (range: EnrollmentDateRange) => {
        let start: number | null = null
        let end: number | null = null

        if (range.start[0] && range.start[1]) {
            const d = new Date(`${range.start[0]}T${range.start[1]}`)
            if (!Number.isNaN(d.getTime())) start = Math.floor(d.getTime() / 1000)
        }
        if (range.end[0] && range.end[1]) {
            const d = new Date(`${range.end[0]}T${range.end[1]}`)
            if (!Number.isNaN(d.getTime())) end = Math.floor(d.getTime() / 1000)
        }

        await ctx.onEdit?.('startDate', start, 'patchStartDate')
        await ctx.onEdit?.('endDate', end, 'patchEndDate')
        setDateDialogOpen(false)
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

    const [div, setDiv] = createSignal<HTMLDivElement | null>(null)

    const remeasure = () => {
        const d = div()

        if (d) {
            setDynamicStickyOffset(d.getBoundingClientRect().height + (props.stickyOffset ?? 0))
        }
    }

    onMount(() => {
        window.addEventListener('resize', remeasure, { passive: true })
        onCleanup(() => window.removeEventListener('resize', remeasure))
    })

    createEffect(remeasure)

    return (
        <Show when={ctx.enrollment}>
            <VStack gap={0} grow>
                <VStack
                    gap={4}
                    class="padded"
                    ref={setDiv}
                    style={{
                        position: 'sticky',
                        top: `calc(var(--sticky-offset) + ${props.stickyOffset ?? 0}px)`,
                        'z-index': 'var(--layer-overlay-high)',
                        background: 'var(--m3c-surface)',
                    }}
                >
                    <HStack wrap>
                        <HStack alignVertical="center" gap={4}>
                            <h1 class="m3-headline-medium">{enrollment().name}</h1>
                            <EditButton field="name" />
                        </HStack>
                        <HStack alignVertical="center" gap={4}>
                            <GroupBadge
                                group={
                                    hasGroup() ? groupsQuery.data?.find(g => g.id === enrollment().groupId) : undefined
                                }
                                placeholder={string.ADD_GROUP()}
                                onClick={() => setGroupDialogOpen(true)}
                                onEdit={() => setGroupDialogOpen(true)}
                            />
                        </HStack>
                    </HStack>

                    <Show
                        when={!ctx.creating}
                        fallback={
                            <VStack grow style={{ 'padding-top': '16px' }}>
                                <EnrollmentDatePickerForm
                                    mode="stretch"
                                    startDate={enrollment().startDate}
                                    endDate={enrollment().endDate}
                                    onSave={onDateSave}
                                    saveOnUnfocus
                                />
                            </VStack>
                        }
                    >
                        <VStack gap={8} style={{ color: 'var(--m3c-on-surface-variant)' }} alignHorizontal="start">
                            <HStack alignVertical="center" gap={8}>
                                <Icon icon={CalendarIcon} size={20} />
                                <HStack alignVertical="center" gap={4}>
                                    <p class={'m3-body-medium'}>{startDateStr()}</p>
                                    <Icon icon={ArrowRightIcon} width={16} height={16} />
                                    <p class="m3-body-medium">{endDateStr()}</p>
                                    <EditButton onClick={startDateEditing} />
                                </HStack>
                            </HStack>

                            <Show when={ctx.onDelete}>
                                <Button variant="tonal-error" onClick={ctx.onDelete} icon={DeleteIcon}>
                                    {string.DELETE_ENROLLMENT()}
                                </Button>
                            </Show>
                        </VStack>
                    </Show>
                </VStack>

                <Show when={activeField()}>
                    {field => (
                        <Portal>
                            <TextFieldDialog
                                open
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

                <Show when={groupsQuery.data}>
                    {g => (
                        <SelectGroupDialog
                            open={groupDialogOpen()}
                            onClose={() => setGroupDialogOpen(false)}
                            onSave={v => ctx.onEdit?.('groupId', v, 'patchGroupId')}
                            groups={g()}
                            value={enrollment().groupId}
                        />
                    )}
                </Show>

                <EnrollmentDatePicker
                    open={dateDialogOpen()}
                    setOpen={setDateDialogOpen}
                    startDate={enrollment().startDate}
                    endDate={enrollment().endDate}
                    onSave={onDateSave}
                />

                <Show when={!ctx.creating}>
                    <SuspenseLoadingPage debugName="EnrollmentSubjects">
                        <EnrollmentSubjectsTab stickyOffset={dynamicStickyOffset()} />
                    </SuspenseLoadingPage>
                </Show>
            </VStack>

            <Show when={ctx.creating && ctx.onSave}>
                <BottomBar>
                    <Button
                        size="m"
                        icon={SaveIcon}
                        onClick={ctx.onSave}
                        style={{ width: '100%' }}
                        disabled={!isValid()}
                    >
                        {string.SAVE()}
                    </Button>
                </BottomBar>
            </Show>
        </Show>
    )
}
