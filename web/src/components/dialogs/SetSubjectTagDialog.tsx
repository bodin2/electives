import LabelOutlineIcon from '@iconify-icons/mdi/label-outline'
import { Icon } from 'm3-solid'
import { createSignal, For } from 'solid-js'
import { SubjectTag } from '../../api'
import { useI18n } from '../../providers/I18nProvider'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { Option, Select } from '../Select'
import { VStack } from '../Stack'

export default function SetSubjectTagDialog(props: {
    open: boolean
    onClose: () => unknown
    onSave: (tag: SubjectTag) => unknown
    initialValue: SubjectTag
}) {
    const { string } = useI18n()
    const [currentTag, setTag] = createSignal<SubjectTag>(props.initialValue)

    let btn!: HTMLButtonElement

    return (
        <Dialog
            quick
            onClose={props.onClose}
            open={props.open}
            headline={<h1 class="m3-headline-small">{string.CATEGORY()}</h1>}
            icon={<Icon fill="var(--m3c-secondary)" icon={LabelOutlineIcon} />}
            centerHeadline
            actions={
                <form method="dialog" style={{ display: 'contents' }}>
                    <Button variant="text" onClick={() => props.onClose()}>
                        {string.CANCEL()}
                    </Button>
                    <Button
                        ref={btn}
                        variant="text"
                        disabled={currentTag() === props.initialValue}
                        onClick={() => {
                            props.onSave(currentTag())
                            props.onClose()
                        }}
                    >
                        {string.SAVE()}
                    </Button>
                </form>
            }
        >
            <VStack as="form" onSubmit={() => btn.click()}>
                <Select label={string.CATEGORY()} onChange={e => setTag(Number(e.currentTarget.value))}>
                    <Option value="" hidden selected={currentTag() === SubjectTag.UNRECOGNIZED}>
                        {string.SELECT_CATEGORY_HINT()}
                    </Option>
                    <For
                        each={Object.entries(SubjectTag).filter(
                            ([_, tag]) => tag !== SubjectTag.UNRECOGNIZED && typeof tag === 'number',
                        )}
                    >
                        {([key, tag]) => (
                            <Option value={tag} selected={currentTag() === tag}>
                                {/* @ts-expect-error: Dynamic key */}
                                {string[`SUBJECT_CATEGORY_${key}`]()}
                            </Option>
                        )}
                    </For>
                </Select>
            </VStack>
        </Dialog>
    )
}
