import SaveIcon from '@iconify-icons/mdi/content-save'
import { type Component, Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { useScrollData } from '../../providers/ScrollDataProvider'
import { Button } from '../Button'
import { VStack } from '../Stack'
import { useSubjectInfoContext } from './SubjectInfo'
import styles from './SubjectInfo.module.css'

export default function SubjectBottomActions(props: { extraContent?: Component }) {
    const ctx = useSubjectInfoContext()
    const { string } = useI18n()
    const scrollData = useScrollData()

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
            <Show when={ctx.onSave && ctx.editable}>
                <Button icon={SaveIcon} class={styles.actionButton} size="m" onClick={() => ctx.onSave?.()}>
                    {string.SAVE()}
                </Button>
            </Show>
        </VStack>
    )
}
