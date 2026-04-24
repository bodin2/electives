import SaveIcon from '@iconify-icons/mdi/content-save'
import { type Component, Match, Show, Switch } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { useScrollData } from '../../providers/ScrollDataProvider'
import { nonNull } from '../../utils'
import { Button } from '../Button'
import AddStudentToSubjectButton from '../buttons/AddStudentToSubjectButton'
import AddTeacherToSubjectButton from '../buttons/AddTeacherToSubjectButton'
import DynamicEnrollButton from '../buttons/DynamicEnrollButton'
import { HStack, VStack } from '../Stack'
import { useSubjectDisplayContext } from './SubjectDisplayContext'
import styles from './SubjectInfo.module.css'
import type { Subject } from '../../api'

export default function SubjectBottomActions(props: { selectedSubject?: Subject; extraContent?: Component }) {
    const ctx = useSubjectDisplayContext()
    const { string } = useI18n()
    const scrollData = useScrollData()

    const subject = () => nonNull(ctx.subject)
    const elective = () => nonNull(ctx.elective)
    const currentTeacherIds = () => subject().teachers.map(t => t.id) ?? []

    return (
        <VStack
            alignHorizontal="center"
            class={styles.actionButtonsContainer}
            style={{
                'border-top-color':
                    scrollData.maxScrollY - scrollData.scrollY > 16 ? 'var(--m3c-outline-variant)' : undefined,
            }}
        >
            {/* @ts-expect-error: Wrong types */}
            <Show when={props.extraContent}>{Content => <div class={styles.actionButton}>{<Content />}</div>}</Show>
            <Show
                when={!ctx.onSave}
                fallback={
                    <Show when={ctx.editable}>
                        <Button icon={SaveIcon} class={styles.actionButton} size="m" onClick={() => ctx.onSave?.()}>
                            {string.SAVE()}
                        </Button>
                    </Show>
                }
            >
                <Switch>
                    <Match when={ctx.user?.isStudent() && ctx.subject?.canUserEnroll(ctx.user)}>
                        <DynamicEnrollButton
                            class={styles.actionButton}
                            elective={elective()}
                            subject={subject()}
                            selectedSubject={props.selectedSubject}
                        />
                    </Match>
                    <Match when={ctx.user?.isTeacher() && ctx.subject?.isTaughtBy(ctx.user)}>
                        <AddStudentToSubjectButton
                            class={styles.actionButton}
                            electiveId={elective().id}
                            subjectId={subject().id}
                        />
                    </Match>
                    <Match when={ctx.editable && ctx.subject}>
                        <HStack grow wrap gap={8} class={styles.actionButton}>
                            <AddStudentToSubjectButton
                                variant="tonal"
                                class={styles.subActionButton}
                                electiveId={elective()?.id}
                                subjectId={subject().id}
                                disabled={!ctx.elective}
                            />
                            <AddTeacherToSubjectButton
                                variant="tonal"
                                class={styles.subActionButton}
                                subjectId={subject().id}
                                electiveId={elective()?.id}
                                disabled={!ctx.elective}
                                currentTeacherIds={currentTeacherIds()}
                            />
                        </HStack>
                    </Match>
                </Switch>
            </Show>
        </VStack>
    )
}
