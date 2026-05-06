import { createQuery, keepPreviousData } from '@tanstack/solid-query'
import { useNavigate } from '@tanstack/solid-router'
import { createEffect, createMemo, createSignal, For, onCleanup, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { subjectMembersQueryOptions } from '../../queries/subjects'
import { nonNull } from '../../utils'
import LoadingPage, { SuspenseLoadingPage } from '../pages/LoadingPage'
import { VStack } from '../Stack'
import { useUserDisplayContext } from '../users/UserDisplayContext'
import { UserListItem } from '../users/UserListItem'
import { useSubjectInfoContext } from './SubjectInfo'
import styles from './SubjectMembersTab.module.css'
import type { User } from '../../api'

interface SubjectMembersTabProps {
    studentRemoveDisabled?: boolean
    onStudentRemove?: (student: User) => unknown
    onTeacherRemove?: (teacher: User) => unknown
}

export default function SubjectMembersTab(props: SubjectMembersTabProps) {
    const { string } = useI18n()
    const { client } = useAPI()
    const ctx = useSubjectInfoContext()
    const counts = useEnrollmentCounts()

    const [outdatedMembers, setOutdatedMembers] = createSignal(false)

    const membersQuery = createQuery(() => {
        const baseOptions = subjectMembersQueryOptions(client, {
            electiveId: ctx.elective?.id ?? -1,
            subjectId: ctx.subject.id,
            withStudents: true,
        })

        return {
            ...baseOptions,
            enabled: !!ctx.elective,
            placeholderData: keepPreviousData,
            select: (data: { students: User[]; teachers: User[] }) => {
                setOutdatedMembers(false)

                const students = [...data.students].sort((a, b) => a.fullName.localeCompare(b.fullName))
                const teachers = [...data.teachers].sort((a, b) => a.fullName.localeCompare(b.fullName))
                return { students, teachers }
            },
        }
    })

    createEffect(() => {
        if (!ctx.elective) return

        const intervalId = setInterval(
            () => {
                if (membersQuery.isFetching || !ctx.elective || !outdatedMembers()) return
                membersQuery.refetch()
            },
            ctx.editable ? 500 : 1500,
        )

        onCleanup(() => clearInterval(intervalId))
    })

    createEffect(prev => {
        if (ctx.elective) {
            // Subscribe to version changes
            const v = counts.getVersion(ctx.elective.id)
            if (v !== prev) {
                setOutdatedMembers(true)
            }

            return v
        }

        return
    })

    const members = createMemo(() =>
        membersQuery.data ? { ...membersQuery.data, capacity: ctx.subject.capacity } : undefined,
    )

    return (
        <SuspenseLoadingPage>
            <Show
                when={ctx.elective}
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
                <Show when={members()} fallback={<LoadingPage />}>
                    {data => (
                        <div class={styles.grid}>
                            <SubjectMembersSection
                                users={data().teachers}
                                title={string.TEACHERS()}
                                onRemove={props.onTeacherRemove}
                                showId={Boolean(props.onTeacherRemove)}
                            />
                            <SubjectMembersSection
                                users={data().students}
                                title={string.STUDENTS()}
                                onRemove={props.onStudentRemove}
                                removeDisabled={props.studentRemoveDisabled}
                                maxCapacity={data().capacity}
                                showId
                            />
                        </div>
                    )}
                </Show>
            </Show>
        </SuspenseLoadingPage>
    )
}

interface SubjectMembersSectionProps {
    users: User[]
    showId?: boolean
    title: string
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
            <h1 class={`m3-label-large text-primary ${styles.header}`}>
                {props.title} ({props.users.length}
                {props.maxCapacity ? `/${props.maxCapacity}` : ''})
            </h1>
            <ul class={styles.list}>
                <Show when={props.users.length === 0}>
                    <p class={`m3-body-medium ${styles.noMembers}`}>
                        {string.NO_X_YET({ object: props.title.toLowerCase() })}
                    </p>
                </Show>
                <For each={props.users}>
                    {user => (
                        <UserListItem
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
