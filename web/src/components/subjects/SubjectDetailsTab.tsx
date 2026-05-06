import AccountCircleIcon from '@iconify-icons/mdi/account-circle-outline'
import HashTagBoxOutlineIcon from '@iconify-icons/mdi/hashtag-box-outline'
import LabelOutlineIcon from '@iconify-icons/mdi/label-outline'
import LocationIcon from '@iconify-icons/mdi/location-on-outline'
import PeopleIcon from '@iconify-icons/mdi/people-outline'
import { createQuery } from '@tanstack/solid-query'
import { mergeClasses } from 'm3-solid'
import { marked } from 'marked'
import { createSignal, Show, Suspense } from 'solid-js'
import { Portal } from 'solid-js/web'
import { SubjectTag } from '../../api'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { teamsQueryOptions } from '../../queries/teams'
import { nonNull } from '../../utils'
import { SelectTeamDialog } from '../dialogs/SelectTeamDialog'
import SetSubjectTagDialog from '../dialogs/SetSubjectTagDialog'
import IconLabel from '../IconLabel'
import { HStack, VStack } from '../Stack'
import styles from './SubjectDetailsTab.module.css'
import { useFieldEditor } from './SubjectFieldEditor'
import SubjectImageSection from './SubjectImageSection'
import { useSubjectInfoContext } from './SubjectInfo'

export default function SubjectDetailsTab() {
    const { string } = useI18n()
    const { client } = useAPI()
    const enrollment = useEnrollmentCounts()
    const ctx = useSubjectInfoContext()
    const { EditButton, startEditing, FieldEditorDialog } = useFieldEditor()

    const enrolledCount = () => {
        if (ctx.elective) return enrollment.getCount(ctx.elective.id, ctx.subject.id)
    }

    const [tagDialogOpen, setTagDialogOpen] = createSignal(false)
    const [teamDialogOpen, setTeamDialogOpen] = createSignal(false)

    const teamsQuery = createQuery(() => ({
        ...teamsQueryOptions(client),
        enabled: client.user?.isAdmin() ?? false,
    }))
    const teams = () => teamsQuery.data

    const descriptionInnerHtml = () => marked(ctx.subject.description ?? '') as string

    const categoryName = () => {
        const tag = ctx.subject.tag
        const key = SubjectTag[tag]
        if (!key || tag === SubjectTag.UNRECOGNIZED) return `${string.SUBJECT_CATEGORY_OTHER()}*`
        // @ts-expect-error: Dynamic key
        return string[`SUBJECT_CATEGORY_${key}`]()
    }

    const teamName = () => {
        const t = teams()
        if (ctx.subject.teamId === undefined || ctx.subject.teamId === null || !t) return undefined
        return t.find(team => team.id === ctx.subject.teamId)?.name
    }

    return (
        <Show when={ctx.subject}>
            <SubjectImageSection onStartEditing={startEditing} />
            <VStack gap={4}>
                <HStack alignVertical="center" gap={4}>
                    <h1 class="m3-headline-medium">{ctx.subject.name}</h1>
                    <EditButton field="name" />
                </HStack>
                <VStack gap={0} style={{ color: 'var(--m3c-on-surface-variant)' }}>
                    <HStack class={styles.infoRow}>
                        <HStack alignVertical="center" gap={4}>
                            <IconLabel
                                icon={LabelOutlineIcon}
                                text={categoryName()}
                                class={mergeClasses(
                                    styles.labelSubText,
                                    ctx.subject.tag === SubjectTag.UNRECOGNIZED && 'text-error',
                                )}
                            />
                            <EditButton onClick={() => setTagDialogOpen(true)} />
                        </HStack>
                        <HStack alignVertical="center" gap={4}>
                            <IconLabel
                                icon={HashTagBoxOutlineIcon}
                                text={ctx.subject.code}
                                class={styles.labelSubText}
                            />
                            <EditButton field="code" />
                        </HStack>
                        <HStack alignVertical="center" gap={4}>
                            <IconLabel icon={LocationIcon} text={ctx.subject.location} class={styles.labelSubText} />
                            <EditButton field="location" />
                        </HStack>
                    </HStack>
                    <HStack class={styles.infoRow}>
                        <HStack alignVertical="center" gap={4}>
                            <Show
                                when={enrolledCount() !== undefined}
                                fallback={
                                    <IconLabel
                                        icon={AccountCircleIcon}
                                        text={string.SUBJECT_CAPACITY_COUNT({
                                            count: ctx.subject.capacity,
                                        })}
                                        class={styles.labelSubText}
                                    />
                                }
                            >
                                <IconLabel
                                    icon={AccountCircleIcon}
                                    text={string.SUBJECT_MEMBERS_COUNT({
                                        count: nonNull(enrolledCount()),
                                        total: ctx.subject.capacity,
                                    })}
                                    class={styles.labelSubText}
                                />
                            </Show>
                            <EditButton field="capacity" />
                            <Show when={ctx.editable}>
                                <IconLabel
                                    icon={PeopleIcon}
                                    text={
                                        <>
                                            {string.TEAM()}:{' '}
                                            <Suspense fallback={string.LOADING()}>
                                                <span class={mergeClasses(!teamName() && 'text-italic')}>
                                                    {teamName() ?? string.NOT_SET()}
                                                </span>
                                            </Suspense>
                                        </>
                                    }
                                    class={mergeClasses(styles.labelSubText)}
                                />
                                <EditButton onClick={() => setTeamDialogOpen(true)} />
                            </Show>
                        </HStack>
                    </HStack>
                </VStack>
            </VStack>
            <HStack alignVertical="start" gap={4} grow alignHorizontal="center">
                <p class={`${styles.description} m3-body-large`} innerHTML={descriptionInnerHtml()} />
                <EditButton field="description" />
            </HStack>

            <FieldEditorDialog />

            <Portal>
                <SetSubjectTagDialog
                    open={tagDialogOpen()}
                    onClose={() => setTagDialogOpen(false)}
                    initialValue={ctx.subject.tag}
                    onSave={tag => ctx.onEdit?.('tag', tag)}
                />
            </Portal>

            <Suspense>
                <Show when={teams()}>
                    <SelectTeamDialog
                        open={teamDialogOpen()}
                        onClose={() => setTeamDialogOpen(false)}
                        onSave={v => ctx.onEdit?.('teamId', v, 'patchTeamId')}
                        teams={nonNull(teams())}
                        value={ctx.subject.teamId ?? null}
                    />
                </Show>
            </Suspense>
        </Show>
    )
}
