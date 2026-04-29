import CloseIcon from '@iconify-icons/mdi/close'
import { useNavigate } from '@tanstack/solid-router'
import { ListItem } from 'm3-solid'
import { type Component, For, Show, Suspense } from 'solid-js'
import AvatarPlaceholder from '../../images/avatar-placeholder.webp'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import Badge from '../Badge'
import { Button } from '../Button'
import LoadingPage from '../pages/LoadingPage'
import { HStack, VStack } from '../Stack'
import { useUserDisplayContext } from '../users/UserDisplayContext'
import styles from './SubjectMembersTab.module.css'
import type { User } from '../../api'

interface SubjectMembersTabProps {
    members: { teachers: User[]; students: User[]; capacity: number } | undefined
    gridClass?: string
    headerClass?: string
    listClass?: string
    noMembersClass?: string
    studentRemoveDisabled?: boolean
    onStudentRemove?: (student: User) => unknown
    onTeacherRemove?: (teacher: User) => unknown
}

export default function SubjectMembersTab(props: SubjectMembersTabProps) {
    const { string } = useI18n()

    return (
        <Suspense fallback={<LoadingPage />}>
            <Show
                when={props.members}
                fallback={
                    <VStack grow alignHorizontal="center" alignVertical="center">
                        <h1 class="m3-headline-medium text-balance">{string.SUBJECT_MEMBERS_PICK_ENROLLMENT_HINT()}</h1>
                        <p class="m3-body-large text-surface-variant text-center text-balance">
                            {string.SUBJECT_MEMBERS_PICK_ENROLLMENT_HINT_DESCRIPTION()}
                        </p>
                        {/* TODO: Add picker here? */}
                    </VStack>
                }
            >
                {data => (
                    <div class={props.gridClass}>
                        <SubjectMembersSection
                            users={data().teachers}
                            title={string.TEACHERS()}
                            headerClass={props.headerClass}
                            listClass={props.listClass}
                            noMembersClass={props.noMembersClass}
                            onRemove={props.onTeacherRemove}
                            showId={Boolean(props.onTeacherRemove)}
                        />
                        <SubjectMembersSection
                            users={data().students}
                            title={string.STUDENTS()}
                            headerClass={props.headerClass}
                            listClass={props.listClass}
                            noMembersClass={props.noMembersClass}
                            onRemove={props.onStudentRemove}
                            removeDisabled={props.studentRemoveDisabled}
                            maxCapacity={data().capacity}
                            showId
                        />
                    </div>
                )}
            </Show>
        </Suspense>
    )
}

interface SubjectMembersSectionProps {
    users: User[]
    showId?: boolean
    title: string
    headerClass?: string
    listClass?: string
    noMembersClass?: string
    onRemove?: (user: User) => unknown
    removeDisabled?: boolean
    maxCapacity?: number
}

function SubjectMembersSection(props: SubjectMembersSectionProps) {
    const { string } = useI18n()
    const currentUser = () => nonNull(useAPI().client.user)
    const userCtx = useUserDisplayContext()
    const navigate = useNavigate()

    return (
        <section>
            <h1 class={`m3-label-large text-primary ${props.headerClass ?? ''}`}>
                {props.title} ({props.users.length}
                {props.maxCapacity ? `/${props.maxCapacity}` : ''})
            </h1>
            <ul class={props.listClass}>
                <Show when={props.users.length === 0}>
                    <p class={`m3-body-medium ${props.noMembersClass ?? ''}`}>
                        {string.NO_X_YET({ object: props.title.toLowerCase() })}
                    </p>
                </Show>
                <For each={props.users}>
                    {user => (
                        <SubjectMemberListItem
                            onClick={() => navigate(userCtx.viewLinkProps(user.id))}
                            user={user}
                            currentUser={currentUser()}
                            onRemove={props.onRemove && (() => props.onRemove?.(user))}
                            removeDisabled={props.removeDisabled}
                            showId={props.showId}
                        />
                    )}
                </For>
            </ul>
        </section>
    )
}

interface SubjectMemberListItemProps {
    user: User
    showId?: boolean
    currentUser?: User
    onClick?: () => unknown
    /**
     * Pass to show a remove button on the right side of the item
     */
    onRemove?: () => unknown
    removeDisabled?: boolean
    /**
     * A custom component to show on the right side of the item.
     * Overrides the remove button if `onRemove` is also provided.
     */
    trailing?: Component<{ user: User }>
}

export function SubjectMemberListItem(props: SubjectMemberListItemProps) {
    const { string } = useI18n()

    return (
        <ListItem
            onClick={props.onClick}
            leading={
                <img class={styles.avatar} src={props.user.avatarUrl || AvatarPlaceholder} alt={string.AVATAR()} />
            }
            headline={
                <HStack alignVertical="center">
                    {props.user.fullName}
                    <HStack gap={4}>
                        <For each={props.user.teams}>{team => <Badge variant="tonal">{team.name}</Badge>}</For>
                        <Show when={props.user.id === props.currentUser?.id}>
                            <Badge variant="tertiary">{string.YOU()}</Badge>
                        </Show>
                    </HStack>
                </HStack>
            }
            supporting={props.showId && props.user.id}
            trailing={
                <Show
                    when={props.trailing}
                    fallback={
                        <Show when={props.onRemove}>
                            <Button
                                disabled={props.removeDisabled}
                                size="xs"
                                aria-label={string.REMOVE_STUDENT_FROM_SUBJECT()}
                                variant="tonal-error"
                                onClick={async e => {
                                    e.stopPropagation()
                                    await nonNull(props.onRemove)()
                                }}
                                icon={CloseIcon}
                                iconType="only"
                            />
                        </Show>
                    }
                >
                    {nonNull(props.trailing)({ user: props.user })}
                </Show>
            }
        />
    )
}
