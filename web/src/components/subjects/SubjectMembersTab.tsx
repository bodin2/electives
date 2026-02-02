import TrashIcon from '@iconify-icons/mdi/trash-can-outline'
import { ListItem } from 'm3-solid'
import { For, Show, Suspense } from 'solid-js'
import AvatarPlaceholder from '../../images/avatar-placeholder.webp'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { nonNull } from '../../utils'
import Badge from '../Badge'
import { Button } from '../Button'
import LoadingPage from '../pages/LoadingPage'
import { HStack } from '../Stack'
import styles from './SubjectMembersTab.module.css'
import type { User } from '../../api'

interface SubjectMembersTabProps {
    members: { teachers: User[]; students: User[] } | undefined
    gridClass?: string
    headerClass?: string
    listClass?: string
    noMembersClass?: string
    onStudentRemove?: (student: User) => unknown
}

export default function SubjectMembersTab(props: SubjectMembersTabProps) {
    const { string } = useI18n()

    return (
        <Suspense fallback={<LoadingPage />}>
            <Show when={props.members} fallback={<LoadingPage />}>
                {data => (
                    <div class={props.gridClass}>
                        <SubjectMembersSection
                            users={data().teachers}
                            title={string.TEACHER()}
                            headerClass={props.headerClass}
                            listClass={props.listClass}
                            noMembersClass={props.noMembersClass}
                        />
                        <SubjectMembersSection
                            users={data().students}
                            title={string.STUDENT()}
                            headerClass={props.headerClass}
                            listClass={props.listClass}
                            noMembersClass={props.noMembersClass}
                            onRemove={props.onStudentRemove}
                        />
                    </div>
                )}
            </Show>
        </Suspense>
    )
}

interface SubjectMembersSectionProps {
    users: User[]
    title: string
    headerClass?: string
    listClass?: string
    noMembersClass?: string
    onRemove?: (user: User) => unknown
}

function SubjectMembersSection(props: SubjectMembersSectionProps) {
    const { string } = useI18n()
    const currentUser = () => nonNull(useAPI().client.user)

    return (
        <section>
            <h1 class={`m3-label-large text-primary ${props.headerClass ?? ''}`}>
                {props.title} ({props.users.length})
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
                            user={user}
                            currentUser={currentUser()}
                            onRemove={props.onRemove && (() => props.onRemove?.(user))}
                        />
                    )}
                </For>
            </ul>
        </section>
    )
}

interface SubjectMemberListItemProps {
    user: User
    currentUser?: User
    /**
     * Pass to show a remove button on the right side of the item
     */
    onRemove?: () => unknown
}

export function SubjectMemberListItem(props: SubjectMemberListItemProps) {
    const { string } = useI18n()

    return (
        <ListItem
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
            supporting={props.user.isStudent() && props.user.id}
            trailing={
                <Show when={props.onRemove}>
                    {onRemove => (
                        <Button
                            aria-label={string.REMOVE_STUDENT_FROM_SUBJECT()}
                            variant="text"
                            onClick={onRemove()}
                            icon={TrashIcon}
                            iconType="only"
                        />
                    )}
                </Show>
            }
        />
    )
}
