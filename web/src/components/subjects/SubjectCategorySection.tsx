import KBArrowDownIcon from '@iconify-icons/mdi/keyboard-arrow-down'
import KBArrowUpIcon from '@iconify-icons/mdi/keyboard-arrow-up'
import { Button } from 'm3-solid'
import { createMemo, createSignal, For, Show } from 'solid-js'
import { useI18n } from '../../providers/I18nProvider'
import { createHashFromString, seededShuffle } from '../../utils/random'
import { HStack, VStack } from '../Stack'
import SubjectListItem from './SubjectListItem'
import type { SubjectTag } from '@bodin2/electives-common/proto/api'
import type { Subject } from '../../api'

const randomSeed = Math.floor(Math.random() * 2147483647)

interface SubjectCategorySectionProps {
    category: keyof typeof SubjectTag
    electiveId: number
    subjects: Subject[]
    defaultExpanded?: boolean
    maxUnexpandedShown: number
    headerClass?: string
    listClass?: string
    thumbnailClass?: string
}

export default function SubjectCategorySection(props: SubjectCategorySectionProps) {
    const [expanded, setExpanded] = createSignal(props.defaultExpanded ?? false)
    const { string } = useI18n()

    const tagName = (): string => {
        const key = `SUBJECT_CATEGORY_${props.category}` as keyof typeof string
        const value = string[key]
        const fallback = string.SUBJECT_CATEGORY_OTHER

        const getValue = (v: typeof value): string => {
            return typeof v === 'function' ? v() : v
        }

        return getValue(value) || getValue(fallback)
    }

    const expandable = () => props.subjects.length > props.maxUnexpandedShown

    const categorySeed = createMemo(() => randomSeed ^ createHashFromString(props.category))
    const subjectRandomized = createMemo(() => seededShuffle(props.subjects, categorySeed()))

    const displayedSubjects = () => subjectRandomized().slice(0, expanded() ? undefined : props.maxUnexpandedShown)

    return (
        <VStack as="section" gap={0}>
            <HStack
                as="header"
                alignVertical="center"
                alignHorizontal={expandable() ? 'space-between' : 'start'}
                class={props.headerClass}
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
            <ul class={props.listClass}>
                <For each={displayedSubjects()}>
                    {subject => (
                        <SubjectListItem
                            electiveId={props.electiveId}
                            subject={subject}
                            thumbnailClass={props.thumbnailClass}
                        />
                    )}
                </For>
            </ul>
        </VStack>
    )
}
