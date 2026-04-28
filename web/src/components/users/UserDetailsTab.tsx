import CloseIcon from '@iconify-icons/mdi/close'
import DeleteIcon from '@iconify-icons/mdi/delete-outline'
import HashTagIcon from '@iconify-icons/mdi/hashtag-box-outline'
import LabelOutlineIcon from '@iconify-icons/mdi/label-outline'
import PencilOutlineIcon from '@iconify-icons/mdi/pencil-outline'
import PlusIcon from '@iconify-icons/mdi/plus'
import { TextField } from 'm3-solid'
import { createSignal, For, Show } from 'solid-js'
import { createStore } from 'solid-js/store'
import { Portal } from 'solid-js/web'
import { type Team, type User, UserType } from '../../api'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import Badge from '../Badge'
import { Button } from '../Button'
import AddTeamToStudentDialog from '../dialogs/AddTeamToStudentDialog'
import TextFieldDialog from '../dialogs/TextFieldDialog'
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
    teams?: Team[]
}

export default function UserDetailsTab(props: UserDetailsTabProps) {
    const { string } = useI18n()
    const ctx = useUserDisplayContext()

    const [avatarDialogOpen, setAvatarDialogOpen] = createSignal(false)
    const [addTeamOpen, setAddTeamOpen] = createSignal(false)

    const [fieldErrors, setFieldErrors] = createStore<Record<'firstName' | 'newPassword' | 'id', boolean | undefined>>({
        id: undefined,
        firstName: undefined,
        newPassword: undefined,
    })

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
                            <h1 class="m3-headline-medium">{user().fullName}</h1>
                            <Show when={user().isStudent()}>
                                <UserTeamsRenderer user={user()} />
                                <HStack gap={4} alignVertical="center" wrap>
                                    <Button
                                        size="xs"
                                        variant="tonal"
                                        icon={PlusIcon}
                                        onClick={() => setAddTeamOpen(true)}
                                    >
                                        {string.ADD_TEAM()}
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

                <VStack as="form" id="user-details" gap={16} onSubmit={e => e.preventDefault()}>
                    <Show when={ctx.creating}>
                        <TextField
                            required
                            variant="outlined"
                            label={ctx.user?.isTeacher() ? string.TEACHER_ID() : string.STUDENT_ID()}
                            onInput={e => {
                                ctx.onEdit?.('id', Number(e.currentTarget.value))

                                e.target.setCustomValidity(
                                    /^\d+$/.test(e.currentTarget.value)
                                        ? ''
                                        : string.ERROR_NUMERIC_VALUE({
                                              field: ctx.user?.isTeacher() ? string.TEACHER_ID() : string.STUDENT_ID(),
                                          }),
                                )

                                setFieldErrors({
                                    id: !e.target.reportValidity(),
                                })

                                setTimeout(() => {
                                    setFieldErrors({
                                        id: undefined,
                                    })
                                })
                            }}
                            error={fieldErrors.id}
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
                        required
                        variant="outlined"
                        autocomplete="given-name"
                        label={string.FIRST_NAME()}
                        value={user().firstName}
                        onInput={e => {
                            ctx.onEdit?.('firstName', e.currentTarget.value)

                            e.target.setCustomValidity(
                                e.currentTarget.value.trim() ? '' : string.ERROR_REQUIRED_FIELD_GENERIC(),
                            )

                            setFieldErrors({
                                firstName: !e.target.reportValidity(),
                            })

                            setTimeout(() => {
                                setFieldErrors({
                                    firstName: undefined,
                                })
                            })
                        }}
                        error={fieldErrors.firstName}
                        // error={!user().firstName.trim()}
                        // supportingText={!user().firstName.trim() ? string.ERROR_REQUIRED_FIELD_GENERIC() : undefined}
                        readOnly={!ctx.editable}
                    />

                    <TextField
                        variant="outlined"
                        autocomplete="additional-name"
                        label={string.MIDDLE_NAME()}
                        value={user().middleName ?? ''}
                        onInput={e => ctx.onEdit?.('middleName', e.currentTarget.value, 'patchMiddleName')}
                        readOnly={!ctx.editable}
                    />

                    <TextField
                        variant="outlined"
                        autocomplete="family-name"
                        label={string.LAST_NAME()}
                        value={user().lastName}
                        onInput={e => ctx.onEdit?.('lastName', e.currentTarget.value)}
                        readOnly={!ctx.editable}
                    />

                    <Show when={ctx.editable}>
                        <TextField
                            required={ctx.creating}
                            variant="outlined"
                            autocomplete={ctx.creating ? 'new-password' : 'current-password'}
                            type="password"
                            label={ctx.creating ? string.PASSWORD() : string.NEW_PASSWORD()}
                            value={ctx.userData?.newPassword ?? ''}
                            onInput={e => {
                                ctx.onEdit?.('newPassword', e.currentTarget.value)

                                const length = e.currentTarget.value.length

                                e.target.setCustomValidity(
                                    (
                                        ctx.creating
                                            ? length < 4
                                            : // 0 length = no password change
                                              length !== 0 && length < 4
                                    )
                                        ? string.PASSWORD_REQUIREMENTS()
                                        : '',
                                )

                                setFieldErrors({
                                    newPassword: !e.target.reportValidity(),
                                })

                                setTimeout(() => {
                                    setFieldErrors({
                                        newPassword: undefined,
                                    })
                                })
                            }}
                            supportingText={string.PASSWORD_REQUIREMENTS()}
                            error={fieldErrors.newPassword}
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
                <AddTeamToStudentDialog
                    open={addTeamOpen()}
                    onClose={team => {
                        setAddTeamOpen(false)
                        if (team && ctx.onEdit) {
                            ctx.onEdit('teams', [...user().teams.map(t => t.toJSON()), team.toJSON()], 'patchTeams')
                        }
                    }}
                    teams={props.teams || []}
                    currentTeamIds={user().teams.map(t => t.id)}
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

function UserTeamsRenderer(props: { user: User }) {
    const ctx = useUserDisplayContext()

    return (
        <For each={props.user.teams}>
            {team => (
                <Badge variant="tonal" class={styles.badge}>
                    {team.name}
                    <Show when={ctx.editable && ctx.onEdit}>
                        <Button
                            size="xs"
                            variant="text"
                            icon={CloseIcon}
                            iconType="only"
                            class={styles.deleteButton}
                            onClick={() =>
                                nonNull(ctx.onEdit)(
                                    'teams',
                                    props.user.teams.filter(t => t.id !== team.id).map(t => t.toJSON()),
                                    'patchTeams',
                                )
                            }
                        />
                    </Show>
                </Badge>
            )}
        </For>
    )
}
