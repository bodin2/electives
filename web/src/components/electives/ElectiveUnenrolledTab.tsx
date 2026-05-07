import { createQuery, skipToken } from '@tanstack/solid-query'
import { useRouter } from '@tanstack/solid-router'
import { createSignal, Show } from 'solid-js'
import { useAPI } from '../../providers/APIProvider'
import { useI18n } from '../../providers/I18nProvider'
import { electiveUnenrolledMembersQueryOptions } from '../../queries/electives'
import { teamsQueryOptions } from '../../queries/teams'
import PaginatedUserList from '../admin/PaginatedUserList'
import { SuspenseLoadingPage } from '../pages/LoadingPage'
import { Option, Select } from '../Select'
import { VStack } from '../Stack'
import { useElectiveInfoContext } from './ElectiveInfo'

export default function ElectiveUnenrolledTab() {
    const ctx = useElectiveInfoContext()
    const { client } = useAPI()
    const router = useRouter()

    const { string } = useI18n()
    const [page, setPage] = createSignal(1)
    const [teamId, setTeamId] = createSignal<number | undefined>(ctx.elective.teamId ?? undefined)

    const teamsQuery = createQuery(() => teamsQueryOptions(client))

    const unenrolledQuery = createQuery(() =>
        electiveUnenrolledMembersQueryOptions(client, ctx.elective.id, teamId() ?? skipToken, page()),
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
                    label={string.TEAM()}
                    // When elective is restricted to a certain team ID,
                    // only those with the same team ID can enroll, so there's no point in enabling the select
                    disabled={ctx.elective.teamId !== null}
                    value={teamId() ?? ''}
                    onInput={e => {
                        const val = e.currentTarget.value
                        setTeamId(val ? Number(val) : undefined)
                        setPage(1)
                    }}
                >
                    <Option value="" hidden selected={teamId() === undefined}>
                        {string.SELECT_TEAM_HINT()}
                    </Option>
                    <Show when={teamsQuery.data}>
                        {t =>
                            t().map(team => (
                                <Option value={team.id} selected={team.id === teamId()}>
                                    {team.name}
                                </Option>
                            ))
                        }
                    </Show>
                </Select>
            </div>

            <Show when={teamId() !== undefined}>
                <SuspenseLoadingPage debugName="ElectiveUnenrolledMembers">
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
