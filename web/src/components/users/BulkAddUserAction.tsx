import AccountMultipleAddIcon from '@iconify-icons/mdi/account-multiple-add'
import { ListItem } from 'm3-solid/src'
import { createSignal, For, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { User } from '~/api/structures'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import { parseUserCSV } from '~/utils/csv'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { HStack, VStack } from '../Stack'
import { UserListItem } from './UserListItem'
import type { AdminAddUserRequest, UserType } from '~/api/types'

interface BulkAddUserActionProps {
    type: UserType
    onComplete?: () => void
}

export function BulkAddUserAction(props: BulkAddUserActionProps) {
    const { string } = useI18n()
    const { client } = useAPI()

    const [importRequests, setImportRequests] = createSignal<AdminAddUserRequest[]>([])
    const [showConfirm, setShowConfirm] = createSignal(false)

    let fileInput: HTMLInputElement | undefined

    const onImportClick = () => fileInput?.click()

    const onFileChange = async (e: Event) => {
        const file = (e.target as HTMLInputElement).files?.[0]
        if (!file) return

        const text = await file.text()
        const requests = parseUserCSV(text, props.type, await client.groups.fetchAll())

        if (requests.length > 0) {
            setImportRequests(requests)
            setShowConfirm(true)
        }

        if (fileInput) fileInput.value = ''
    }

    const onConfirmImport = async () => {
        await client.users.admin.bulkAdd(importRequests())
        setShowConfirm(false)
        props.onComplete?.()
    }

    return (
        <>
            <input type="file" accept=".csv" ref={fileInput} style={{ display: 'none' }} onChange={onFileChange} />
            <Button size="xs" iconType="only" variant="tonal" icon={AccountMultipleAddIcon} onClick={onImportClick} />
            <Portal>
                <Dialog
                    open={showConfirm()}
                    onClose={() => {
                        setShowConfirm(false)
                        setImportRequests([])
                    }}
                    headline={string.BULK_ADD_USERS_CONFIRM_TITLE({ count: importRequests().length })}
                    actions={
                        <HStack gap={8} alignHorizontal="end" style={{ width: '100%' }}>
                            <Button variant="text" onClick={() => setShowConfirm(false)}>
                                {string.CANCEL()}
                            </Button>
                            <Button onClick={onConfirmImport}>{string.ADD()}</Button>
                        </HStack>
                    }
                >
                    <VStack gap={16} style={{ 'max-width': '384px' }}>
                        <p class="m3-body-medium">
                            {string.BULK_ADD_USERS_CONFIRM_DESCRIPTION({ count: importRequests().length })}
                        </p>
                        <VStack gap={0} style={{ 'max-height': '208px', 'overflow-y': 'auto' }}>
                            <For each={importRequests().slice(0, 10)}>
                                {req => <UserListItem user={new User(client, nonNull(req.user))} showId />}
                            </For>
                            <Show when={importRequests().length > 10}>
                                <ListItem
                                    headline={string.ADD_ELLIPSIS()}
                                    supportingText={string.USERS_COUNT({ count: importRequests().length - 10 })}
                                />
                            </Show>
                        </VStack>
                    </VStack>
                </Dialog>
            </Portal>
        </>
    )
}
