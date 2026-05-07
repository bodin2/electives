import { SubjectTag } from '@bodin2/electives-common/proto/api'
import KBArrowDownIcon from '@iconify-icons/mdi/keyboard-arrow-down'
import KBArrowUpIcon from '@iconify-icons/mdi/keyboard-arrow-up'
import { Button } from 'm3-solid'
import { createMemo, createSignal, For, type JSX, Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { createHashFromString, seededShuffle } from '../../utils/random'
import { HStack, VStack } from '../Stack'
import styles from './SubjectCategorySection.module.css'
import SubjectListItem from './SubjectListItem'
import type { LinkProps } from '@tanstack/solid-router'
import type { Elective, Subject } from '../../api'

const randomSeed = Math.floor(Math.random() * 2147483647)

interface SubjectCategorySectionProps {
    category: SubjectTag | string
    subjects: Subject[]
    defaultExpanded?: boolean
    maxUnexpandedShown: number
    noRandom?: boolean
    editable?: boolean
    elective?: Elective
    itemActions?: (subject: Subject) => JSX.Element
    viewLinkProps?: (subjectId: number) => LinkProps
    selectedIds?: number[]
    onSubjectClick?: (subject: Subject) => void
}

export default function SubjectCategorySection(props: SubjectCategorySectionProps) {
    const [expanded, setExpanded] = createSignal(props.defaultExpanded ?? false)
    const { string } = useI18n()

    const tagName = (): string => {
        if (typeof props.category === 'string') return props.category

        const key = `SUBJECT_CATEGORY_${SubjectTag[props.category]}` as keyof typeof string
        const value = string[key]
        const fallback = string.SUBJECT_CATEGORY_OTHER

        const getValue = (v: typeof value): string => {
            return typeof v === 'function' ? v() : v
        }

        return getValue(value) || getValue(fallback)
    }

    const expandable = () => props.subjects.length > props.maxUnexpandedShown

    const categorySeed = createMemo(
        () => randomSeed ^ (typeof props.category === 'string' ? createHashFromString(props.category) : props.category),
    )
    const subjectRandomized = createMemo(() =>
        props.noRandom ? props.subjects : seededShuffle(props.subjects, categorySeed()),
    )

    const displayedSubjects = () => subjectRandomized().slice(0, expanded() ? undefined : props.maxUnexpandedShown)

    return (
        <VStack as="section" gap={0}>
            <HStack
                as="header"
                alignVertical="center"
                alignHorizontal={expandable() ? 'space-between' : 'start'}
                class={styles.header}
            >
                <h1 class="m3-title-large">
                    {tagName()} ({props.subjects.length})
                </h1>
                <Show when={expandable()}>
                    <Button
                        variant="tonal"
                        onClick={() => setExpanded(!expanded())}
                        icon={expanded() ? KBArrowUpIcon : KBArrowDownIcon}
                    >
                        {expanded() ? string.VIEW_LESS() : string.VIEW_ALL()}
                    </Button>
                </Show>
            </HStack>
            <ul class={styles.list}>
                <For each={displayedSubjects()}>
                    {subject => (
                        <SubjectListItem
                            subject={subject}
                            editable={props.editable}
                            electiveId={props.elective?.id}
                            actions={props.itemActions?.(subject)}
                            linkProps={props.viewLinkProps?.(subject.id)}
                            selected={props.selectedIds?.includes(subject.id)}
                            onClick={props.onSubjectClick && (() => props.onSubjectClick!(subject))}
                        />
                    )}
                </For>
            </ul>
        </VStack>
    )
}
