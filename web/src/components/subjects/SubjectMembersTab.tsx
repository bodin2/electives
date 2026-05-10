import { createQuery, keepPreviousData } from '@tanstack/solid-query'
import { useNavigate } from '@tanstack/solid-router'
import { createEffect, createMemo, createSignal, For, onCleanup, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useEnrollmentCounts } from '../../providers/EnrollmentCountsProvider'
import { useI18n } from '../../providers/I18nProvider'
import { subjectMembersQueryOptions } from '../../queries/subjects'
import { nonNull } from '../../utils'
import LoadingPage from '../pages/LoadingPage'
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
            enrollmentId: ctx.enrollment?.id ?? -1,
            subjectId: ctx.subject.id,
            withStudents: true,
        })

        return {
            ...baseOptions,
            enabled: !!ctx.enrollment,
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
        if (!ctx.enrollment) return

        const intervalId = setInterval(
            () => {
                if (membersQuery.isFetching || !ctx.enrollment || !outdatedMembers()) return
                membersQuery.refetch()
            },
            ctx.editable ? 500 : 1500,
        )

        onCleanup(() => clearInterval(intervalId))
    })

    createEffect(prev => {
        if (ctx.enrollment) {
            // Subscribe to version changes
            const v = counts.getVersion(ctx.enrollment.id)
            if (prev !== undefined && v !== prev) {
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
        <Show
            when={ctx.enrollment}
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
            <Show when={members()} fallback={<LoadingPage debugName="SubjectMembersShow" />}>
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
