import { createQuery, skipToken } from '@tanstack/solid-query'
import { useRouter } from '@tanstack/solid-router'
import { createSignal, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { enrollmentUnenrolledMembersQueryOptions } from '../../queries/enrollments'
import { groupsQueryOptions } from '../../queries/groups'
import PaginatedUserList from '../admin/PaginatedUserList'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { Option, Select } from '../Select'
import { VStack } from '../Stack'
import { useEnrollmentInfoContext } from './EnrollmentInfo'

export default function EnrollmentUnenrolledTab() {
    const ctx = useEnrollmentInfoContext()
    const { client } = useAPI()
    const router = useRouter()

    const { string } = useI18n()
    const [page, setPage] = createSignal(1)
    const [groupId, setGroupId] = createSignal<number | undefined>(ctx.enrollment.groupId ?? undefined)

    const groupsQuery = createQuery(() => groupsQueryOptions(client))

    const unenrolledQuery = createQuery(() =>
        enrollmentUnenrolledMembersQueryOptions(client, ctx.enrollment.id, groupId() ?? skipToken, page()),
    )

    return (
        <VStack style={{ '--sticky-offset': '48px' }}>
            <div
                class="padded"
                style={{
                    position: 'sticky',
                    top: 'var(--sticky-offset)',
                    background: 'var(--m3c-surface)',
                    'z-index': 10,
                    'padding-bottom': '8px',
                }}
            >
                <Select
                    label={string.GROUP()}
                    // When enrollment is restricted to a certain group ID,
                    // only those with the same group ID can enroll, so there's no point in enabling the select
                    disabled={ctx.enrollment.groupId !== null}
                    value={groupId() ?? ''}
                    onInput={e => {
                        const val = e.currentTarget.value
                        setGroupId(val ? Number(val) : undefined)
                        setPage(1)
                    }}
                >
                    <Option value="" hidden selected={groupId() === undefined}>
                        {string.SELECT_GROUP_HINT()}
                    </Option>
                    <Show when={groupsQuery.data}>
                        {g =>
                            g().map(group => (
                                <Option value={group.id} selected={group.id === groupId()}>
                                    {group.name}
                                </Option>
                            ))
                        }
                    </Show>
                </Select>
            </div>

            <Show when={groupId() !== undefined}>
                <SuspenseLoadingPage debugName="EnrollmentUnenrolledMembers">
                    <Show when={unenrolledQuery.data}>
                        {d => (
                            <PaginatedUserList
                                data={d()}
                                page={page()}
                                onPageChange={setPage}
                                isLoading={unenrolledQuery.isLoading || unenrolledQuery.isFetching}
                                onClick={user =>
                                    router.navigate({
                                        to: '/manage/users/$userId',
                                        params: { userId: String(user.id) },
                                    })
                                }
                            />
                        )}
                    </Show>
                </SuspenseLoadingPage>
            </Show>
        </VStack>
    )
}
