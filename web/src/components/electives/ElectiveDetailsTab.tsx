import AddIcon from '@iconify-icons/mdi/add'
import ArrowRightIcon from '@iconify-icons/mdi/arrow-right'
import CalendarIcon from '@iconify-icons/mdi/calendar-clock-outline'
import SaveIcon from '@iconify-icons/mdi/content-save'
import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import { createQuery } from '@tanstack/solid-query'
import { Icon } from 'm3-solid'
import { createEffect, createSignal, onCleanup, onMount, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { useAPI } from '../../providers/APIProvider'
import { type I18nApi, useI18n } from '../../providers/I18nProvider'
import { teamsQueryOptions } from '../../queries/teams'
import { nonNull } from '../../utils'
import { formatDuration } from '../../utils/date'
import Badge from '../Badge'
import BottomBar from '../BottomBar'
import { Button } from '../Button'
import TextFieldDialog from '../dialogs/base/TextFieldDialog'
import { SelectTeamDialog } from '../dialogs/SelectTeamDialog'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { HStack, VStack } from '../Stack'
import { useElectiveInfoContext } from './ElectiveInfo'
import ElectiveSubjectsTab from './ElectiveSubjectsTab'
import { EnrollmentDatePicker, EnrollmentDatePickerForm, type EnrollmentDateRange } from './EnrollmentDatePicker'

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

export default function ElectiveDetailsTab(props: { stickyOffset?: number }) {
    const { string, locale } = useI18n()
    const { client } = useAPI()
    const ctx = useElectiveInfoContext()

    const elective = () => nonNull(ctx.elective)
    const hasTeam = () => elective().teamId !== null

    // Default size = 152px
    const [dynamicStickyOffset, setDynamicStickyOffset] = createSignal(152 + (props.stickyOffset ?? 0))

    const [activeField, setActiveField] = createSignal<ActiveField | null>(null)
    const teamsQuery = createQuery(() => teamsQueryOptions(client))
    const [teamDialogOpen, setTeamDialogOpen] = createSignal(false)

    const [dateDialogOpen, setDateDialogOpen] = createSignal(false)

    const startDateStr = () =>
        elective().startDate ? formatDuration(locale, nonNull(elective().startDate)) : string.NOT_SET()
    const endDateStr = () =>
        elective().endDate ? formatDuration(locale, nonNull(elective().endDate)) : string.NOT_SET()

    const startEditing = (key: string) => {
        const def = FIELDS[key]
        let label = string.NAME()
        if (!def.required) {
            label += ` ${string.IF_ANY()}`
        }

        setActiveField({
            ...def,
            label,
            initialValue: elective().name,
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
        <Show when={ctx.elective}>
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
                            <h1 class="m3-headline-medium">{elective().name}</h1>
                            <EditButton field="name" />
                        </HStack>
                        <HStack alignVertical="center" gap={4}>
                            <Badge
                                style={{ cursor: 'pointer' }}
                                onClick={() => setTeamDialogOpen(true)}
                                variant="tonal"
                            >
                                <HStack gap={4}>
                                    {hasTeam() && teamsQuery.data
                                        ? teamsQuery.data.find(t => t.id === elective().teamId)?.name
                                        : string.ADD_TEAM()}
                                    <Button
                                        iconType="only"
                                        variant="text"
                                        size="xs"
                                        icon={hasTeam() ? PencilOutlineIcon : AddIcon}
                                        style={{ width: '20px', height: '20px' }}
                                    />
                                </HStack>
                            </Badge>
                        </HStack>
                    </HStack>

                    <Show
                        when={!ctx.creating}
                        fallback={
                            <VStack grow style={{ 'padding-top': '16px' }}>
                                <EnrollmentDatePickerForm
                                    mode="stretch"
                                    startDate={elective().startDate}
                                    endDate={elective().endDate}
                                    onSave={onDateSave}
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

                <Show when={teamsQuery.data}>
                    {t => (
                        <SelectTeamDialog
                            open={teamDialogOpen()}
                            onClose={() => setTeamDialogOpen(false)}
                            onSave={v => ctx.onEdit?.('teamId', v, 'patchTeamId')}
                            teams={t()}
                            value={elective().teamId}
                        />
                    )}
                </Show>

                <EnrollmentDatePicker
                    open={dateDialogOpen()}
                    setOpen={setDateDialogOpen}
                    startDate={elective().startDate}
                    endDate={elective().endDate}
                    onSave={onDateSave}
                />

                <Show when={!ctx.creating}>
                    <SuspenseLoadingPage debugName="ElectiveSubjects">
                        <ElectiveSubjectsTab stickyOffset={dynamicStickyOffset()} />
                    </SuspenseLoadingPage>
                </Show>
            </VStack>

            <Show when={ctx.creating && ctx.onSave}>
                <BottomBar>
                    <Button size="m" icon={SaveIcon} onClick={ctx.onSave} style={{ width: '100%' }}>
                        {string.SAVE()}
                    </Button>
                </BottomBar>
            </Show>
        </Show>
    )
}
