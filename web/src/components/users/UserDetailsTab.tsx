import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import HashTagIcon from '@iconify-icons/mdi/hashtag-box-outline'
import LabelOutlineIcon from '@iconify-icons/mdi/label-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import { TextField } from 'm3-solid/src'
import { createSignal, Show } from 'solid-js'
import { createStore } from 'solid-js/store'
import { Portal } from 'solid-js/web'
import { type Group, GroupType, type User, UserType } from '~/api'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import { Badges, GroupBadge } from '../Badges'
import { Button } from '../Button'
import AddGroupToStudentDialog from '../dialogs/AddGroupToStudentDialog'
import TextFieldDialog from '../dialogs/base/TextFieldDialog'
import IconLabel from '../IconLabel'
import { Option, Select } from '../Select'
import { HStack, VStack } from '../Stack'
import UserAvatar from './UserAvatar'
import styles from './UserDetailsTab.module.css'
import { useUserDisplayContext } from './UserDisplayContext'

interface UserDetailsTabProps {
    avatarClass?: string
    avatarPlaceholderClass?: string
    descriptionClass?: string
    labelClass?: string
    initialType?: UserType
    groups?: Group[]
}

export default function UserDetailsTab(props: UserDetailsTabProps) {
    const { string } = useI18n()
    const ctx = useUserDisplayContext()

    const [avatarDialogOpen, setAvatarDialogOpen] = createSignal(false)
    const [addGroupOpen, setAddGroupOpen] = createSignal(false)
    const [editingSlot, setEditingSlot] = createSignal<GroupType | null>(null)

    const [fieldErrors, setFieldErrors] = createStore<Record<string, string | undefined>>({})

    const validate = (field: string, input: HTMLInputElement) => {
        input.setCustomValidity('')

        switch (field) {
            case 'id': {
                if (!/^\d+$/.test(input.value)) {
                    input.setCustomValidity(
                        string.ERROR_NUMERIC_VALUE({
                            field: ctx.user?.isTeacher() ? string.TEACHER_ID() : string.STUDENT_ID(),
                        }),
                    )
                }

                break
            }
            case 'firstName': {
                if (!input.value.trim()) {
                    input.setCustomValidity(string.ERROR_REQUIRED_FIELD_GENERIC())
                }

                break
            }
            case 'newPassword': {
                const length = input.value.length
                const isValid = ctx.creating ? length >= 4 : length === 0 || length >= 4
                if (!isValid) {
                    input.setCustomValidity(string.PASSWORD_REQUIREMENTS())
                }

                break
            }
        }

        // Update the store with the current validation message
        setFieldErrors(field, input.validationMessage || undefined)
    }

    const user = () => nonNull(ctx.user)

    const userTypeName = () => {
        const type = user().type
        const key = UserType[type]
        if (!key) return string.USER_TYPE_STUDENT()
        // @ts-expect-error: Dynamic key
        return string[`USER_TYPE_${key}`]()
    }

    const editAvatar = (e?: Event) => {
        e?.stopPropagation()
        if (!ctx.editable || !ctx.onEdit) return
        setAvatarDialogOpen(true)
    }

    return (
        <Show when={ctx.user}>
            <VStack gap={32}>
                <HStack gap={32} alignVertical="center" wrap>
                    {/** biome-ignore lint/a11y/noStaticElementInteractions: Intentional */}
                    {/** biome-ignore lint/a11y/useKeyWithClickEvents: Intentional */}
                    <div
                        style={{
                            position: 'relative',
                            width: 'fit-content',
                            cursor: ctx.editable ? 'pointer' : 'default',
                        }}
                        onClick={editAvatar}
                        tabindex="-1"
                    >
                        <UserAvatar
                            imageUrl={user().avatarUrl}
                            class={props.avatarClass}
                            placeholderClass={props.avatarPlaceholderClass}
                        />
                        <Show when={ctx.editable && ctx.onEdit}>
                            <Button
                                size="xs"
                                variant="tonal"
                                icon={PencilOutlineIcon}
                                iconType="only"
                                onClick={editAvatar}
                                style={{ position: 'absolute', bottom: 0, right: 0 }}
                            />
                        </Show>
                    </div>

                    <VStack gap={4} grow>
                        <HStack alignVertical="center" gap={8} wrap>
                            <h1 class="m3-headline-medium">{user().displayName}</h1>
                            <Show when={user().isStudent()}>
                                <FixedSlotBadge user={user()} slot={GroupType.GRADE} onEdit={setEditingSlot} required />
                                <FixedSlotBadge user={user()} slot={GroupType.ROOM} onEdit={setEditingSlot} required />
                                <FixedSlotBadge user={user()} slot={GroupType.PROGRAM} onEdit={setEditingSlot} />
                                <BadgeListEditor user={user()} />
                                <HStack gap={4} alignVertical="center" wrap>
                                    <Button
                                        size="xs"
                                        variant="tonal"
                                        icon={PlusIcon}
                                        onClick={() => setAddGroupOpen(true)}
                                    >
                                        {string.ADD_GROUP()}
                                    </Button>
                                </HStack>
                            </Show>
                        </HStack>

                        <HStack class={styles.infoRow}>
                            <Show when={!ctx.creating}>
                                <IconLabel icon={HashTagIcon} text={String(user().id)} class={props.labelClass} />
                                <IconLabel icon={LabelOutlineIcon} text={userTypeName()} class={props.labelClass} />
                            </Show>
                        </HStack>
                    </VStack>
                </HStack>

                <VStack
                    as="form"
                    id="user-details"
                    gap={16}
                    onSubmit={e => e.preventDefault()}
                    ref={form => {
                        form.addEventListener('trysubmit', e => {
                            e.preventDefault()

                            for (const input of form.elements) {
                                const htmlInput = input as HTMLInputElement
                                if (htmlInput.name) {
                                    validate(htmlInput.name, htmlInput)
                                }
                            }

                            form.requestSubmit()
                        })
                    }}
                >
                    <Show when={ctx.creating}>
                        <TextField
                            required
                            name="id"
                            variant="outlined"
                            label={ctx.user?.isTeacher() ? string.TEACHER_ID() : string.STUDENT_ID()}
                            error={fieldErrors.id !== undefined}
                            supportingText={fieldErrors.id}
                            onInput={e => {
                                ctx.onEdit?.('id', Number(e.currentTarget.value))
                                validate('id', e.target)
                            }}
                        />

                        <Select
                            label={string.TYPE()}
                            value={user().type}
                            onInput={e => ctx.onEdit?.('type', Number(e.currentTarget.value))}
                        >
                            <Option value={UserType.STUDENT} selected={user().type === UserType.STUDENT}>
                                {string.USER_TYPE_STUDENT()}
                            </Option>
                            <Option value={UserType.TEACHER} selected={user().type === UserType.TEACHER}>
                                {string.USER_TYPE_TEACHER()}
                            </Option>
                        </Select>
                    </Show>

                    <TextField
                        name="prefix"
                        variant="outlined"
                        autocomplete="honorific-prefix"
                        label={string.PREFIX()}
                        value={user().prefix ?? ''}
                        onInput={e => {
                            ctx.onEdit?.('prefix', e.currentTarget.value, 'patchPrefix')
                            validate('prefix', e.target)
                        }}
                        disabled={!ctx.editable}
                    />

                    <TextField
                        required
                        name="firstName"
                        variant="outlined"
                        autocomplete="given-name"
                        label={string.FIRST_NAME()}
                        value={user().firstName}
                        onInput={e => {
                            ctx.onEdit?.('firstName', e.currentTarget.value)
                            validate('firstName', e.target)
                        }}
                        supportingText={fieldErrors.firstName}
                        error={fieldErrors.firstName !== undefined}
                        readOnly={!ctx.editable}
                    />

                    <TextField
                        name="middleName"
                        variant="outlined"
                        autocomplete="additional-name"
                        label={string.MIDDLE_NAME()}
                        value={user().middleName ?? ''}
                        onInput={e => {
                            ctx.onEdit?.('middleName', e.currentTarget.value, 'patchMiddleName')
                            validate('middleName', e.target)
                        }}
                        readOnly={!ctx.editable}
                    />

                    <TextField
                        name="lastName"
                        variant="outlined"
                        autocomplete="family-name"
                        label={string.LAST_NAME()}
                        value={user().lastName}
                        onInput={e => {
                            ctx.onEdit?.('lastName', e.currentTarget.value)
                            validate('lastName', e.target)
                        }}
                        readOnly={!ctx.editable}
                    />

                    <Show when={ctx.editable}>
                        <TextField
                            required={ctx.creating}
                            name="newPassword"
                            variant="outlined"
                            autocomplete={ctx.creating ? 'new-password' : 'current-password'}
                            type="text"
                            label={ctx.creating ? string.PASSWORD() : string.NEW_PASSWORD()}
                            value={ctx.userData?.newPassword ?? ''}
                            onInput={e => {
                                ctx.onEdit?.('newPassword', e.currentTarget.value)
                                validate('newPassword', e.target)
                            }}
                            supportingText={fieldErrors.newPassword || string.PASSWORD_REQUIREMENTS()}
                            error={fieldErrors.newPassword !== undefined}
                        />
                    </Show>
                </VStack>
                <div>
                    <Show when={ctx.user && ctx.editable && !ctx.creating && ctx.onDelete}>
                        <Button icon={DeleteIcon} variant="tonal-error" onClick={ctx.onDelete}>
                            {string.DELETE_USER()}
                        </Button>
                    </Show>
                </div>
            </VStack>

            <Portal>
                <AddGroupToStudentDialog
                    open={addGroupOpen()}
                    onClose={group => {
                        setAddGroupOpen(false)
                        if (group && ctx.onEdit) {
                            ctx.onEdit(
                                'groups',
                                [...user().groups.map(g => g.toJSON()), group.toJSON()].sort((a, b) => a.id - b.id),
                                'patchGroups',
                            )
                        }
                    }}
                    groups={(props.groups || []).filter(g => g.type === GroupType.CUSTOM)}
                    currentGroupIds={user().groups.map(g => g.id)}
                />
                <AddGroupToStudentDialog
                    open={editingSlot() !== null}
                    onClose={group => {
                        const slot = editingSlot()
                        setEditingSlot(null)
                        if (slot === null || !group || !ctx.onEdit) return
                        // Replace the existing same-typed group (if any) with the picked one
                        const next = [
                            ...user()
                                .groups.filter(g => g.type !== slot)
                                .map(g => g.toJSON()),
                            group.toJSON(),
                        ].sort((a, b) => a.id - b.id)
                        ctx.onEdit('groups', next, 'patchGroups')
                    }}
                    groups={(props.groups || []).filter(g => g.type === editingSlot())}
                    currentGroupIds={[]}
                    initialId={user().groups.find(g => g.type === editingSlot())?.id}
                    headline={slotHeadline(editingSlot(), string)}
                    selectLabel={slotLabel(editingSlot(), string)}
                    selectPlaceholder={slotPlaceholder(editingSlot(), string)}
                    onClear={
                        // Only the PROGRAM slot is server-side optional (gradeId/roomId are required).
                        editingSlot() === GroupType.PROGRAM && ctx.onEdit
                            ? () => {
                                  const next = user()
                                      .groups.filter(g => g.type !== GroupType.PROGRAM)
                                      .map(g => g.toJSON())
                                  nonNull(ctx.onEdit)('groups', next, 'patchGroups')
                              }
                            : undefined
                    }
                />
                <TextFieldDialog
                    dialog={{ quick: true }}
                    open={avatarDialogOpen()}
                    onClose={() => setAvatarDialogOpen(false)}
                    onSave={val => ctx.onEdit?.('avatarUrl', val, 'patchAvatarUrl')}
                    label={string.AVATAR()}
                    initialValue={user().avatarUrl ?? ''}
                />
            </Portal>
        </Show>
    )
}

function BadgeListEditor(props: { user: User }) {
    const ctx = useUserDisplayContext()

    return (
        <Badges
            groups={props.user.groups}
            types={[GroupType.CUSTOM]}
            onRemove={
                ctx.editable && ctx.onEdit
                    ? group =>
                          nonNull(ctx.onEdit)(
                              'groups',
                              props.user.groups.filter(g => g.id !== group.id).map(g => g.toJSON()),
                              'patchGroups',
                          )
                    : undefined
            }
        />
    )
}

function FixedSlotBadge(props: { user: User; slot: GroupType; required?: boolean; onEdit: (slot: GroupType) => void }) {
    const { string } = useI18n()
    const ctx = useUserDisplayContext()
    const current = () => props.user.groups.find(g => g.type === props.slot)

    return (
        <GroupBadge
            group={current()}
            fallbackType={props.slot}
            placeholder={slotPlaceholder(props.slot, string)}
            required={props.required}
            onEdit={ctx.editable && ctx.onEdit ? () => props.onEdit(props.slot) : undefined}
        />
    )
}

type StringApi = ReturnType<typeof useI18n>['string']

function slotPlaceholder(slot: GroupType | null, string: StringApi): string {
    switch (slot) {
        case GroupType.GRADE:
            return string.SELECT_GRADE_HINT()
        case GroupType.ROOM:
            return string.SELECT_ROOM_HINT()
        case GroupType.PROGRAM:
            return string.SELECT_PROGRAM_HINT()
        default:
            return string.SELECT_GROUP_HINT()
    }
}

function slotLabel(slot: GroupType | null, string: StringApi): string {
    switch (slot) {
        case GroupType.GRADE:
            return string.GROUP_TYPE_GRADE()
        case GroupType.ROOM:
            return string.GROUP_TYPE_ROOM()
        case GroupType.PROGRAM:
            return string.GROUP_TYPE_PROGRAM()
        default:
            return string.GROUPS()
    }
}

function slotHeadline(slot: GroupType | null, string: StringApi): string {
    return slotPlaceholder(slot, string)
}
