import AccountMultipleAddIcon from '@iconify-icons/mdi/account-multiple-add'
import DownloadIcon from '@iconify-icons/mdi/download-outline'
import FileUploadIcon from '@iconify-icons/mdi/file-upload-outline'
import { ListItem, Button as M3Button } from 'm3-solid/src'
import { createSignal, For, Show } from 'solid-js'
import { Portal } from 'solid-js/web'
import { User } from '~/api/structures'
import { type AdminAddUserRequest, GroupType, type UserType } from '~/api/types'
import { useAPI } from '~/providers/APIProvider'
import { useI18n } from '~/providers/I18nProvider'
import { nonNull } from '~/utils'
import {
    AmbiguousGroupError,
    CSVParseError,
    InvalidFieldsError,
    InvalidGroupError,
    parseUserCSV,
    WrongGroupTypeError,
} from '~/utils/csv'
import { Button } from '../Button'
import { Dialog } from '../Dialog'
import { HStack, VStack } from '../Stack'
import { UserListItem } from './UserListItem'

interface BulkAddUserActionProps {
    type: UserType
    onComplete?: () => void
}

export function BulkAddUserAction(props: BulkAddUserActionProps) {
    const { string } = useI18n()
    const { client } = useAPI()

    const [importRequests, setImportRequests] = createSignal<AdminAddUserRequest[]>([])
    const [showPicker, setShowPicker] = createSignal(false)
    const [showConfirm, setShowConfirm] = createSignal(false)
    const [importErrors, setImportErrors] = createSignal<Error[]>([])
    const [showErrors, setShowErrors] = createSignal(false)

    let fileInput: HTMLInputElement | undefined

    const openFilePicker = () => {
        setShowPicker(false)
        fileInput?.click()
    }

    const formatError = (err: Error) => {
        if (err instanceof InvalidGroupError) {
            return string.BULK_ADD_USERS_ERROR_CANNOT_FIND_GROUP({ groupName: <strong>{err.query}</strong> })
        }
        if (err instanceof AmbiguousGroupError) {
            return string.BULK_ADD_USERS_ERROR_AMBIGUOUS_GROUP({ groupName: <strong>{err.query}</strong> })
        }
        if (err instanceof WrongGroupTypeError) {
            return string.BULK_ADD_USERS_ERROR_WRONG_GROUP_TYPE({
                groupName: <strong>{err.group.name}</strong>,
                expectedType: <strong>{GroupType[err.expected]}</strong>,
            })
        }
        if (err instanceof InvalidFieldsError) return string.BULK_ADD_USERS_ERROR_INVALID_FIELDS()
        if (err instanceof CSVParseError) return string.BULK_ADD_USERS_ERROR_BAD_FORMAT()
        return String(err)
    }

    const onFileChange = async (e: Event) => {
        const file = (e.target as HTMLInputElement).files?.[0]
        if (!file) return

        const text = await file.text()
        try {
            const requests = parseUserCSV(text, props.type, await client.groups.fetchAll())
            if (requests.length > 0) {
                setImportRequests(requests)
                setShowConfirm(true)
                return
            }

            throw new CSVParseError(string.BULK_ADD_USERS_ERROR_BAD_FORMAT(), 0)
        } catch (err) {
            if (err instanceof AggregateError) {
                setImportErrors(err.errors as Error[])
                setShowErrors(true)
            } else if (err instanceof CSVParseError) {
                setImportErrors([err])
                setShowErrors(true)
            } else {
                throw err
            }
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
            <Button
                size="xs"
                iconType="only"
                variant="tonal"
                icon={AccountMultipleAddIcon}
                onClick={() => setShowPicker(true)}
            />
            <Portal>
                <Dialog
                    open={showPicker()}
                    onClose={() => setShowPicker(false)}
                    headline={string.BULK_ADD_USERS_PICK_TITLE()}
                    actions={
                        <HStack gap={8} alignHorizontal="end" style={{ width: '100%' }}>
                            <Button variant="text" onClick={() => setShowPicker(false)}>
                                {string.CANCEL()}
                            </Button>
                        </HStack>
                    }
                >
                    <VStack gap={16} style={{ 'max-width': '384px' }}>
                        <p class="m3-body-medium">{string.BULK_ADD_USERS_PICK_DESCRIPTION()}</p>
                        <VStack gap={8}>
                            <M3Button
                                variant="tonal"
                                icon={DownloadIcon}
                                href="/assets/example_students.csv"
                                download=""
                            >
                                {string.BULK_ADD_USERS_STUDENTS_EXAMPLE()}
                            </M3Button>
                            <M3Button
                                variant="tonal"
                                icon={DownloadIcon}
                                href="/assets/example_teachers.csv"
                                download=""
                            >
                                {string.BULK_ADD_USERS_TEACHERS_EXAMPLE()}
                            </M3Button>
                            <Button variant="filled" icon={FileUploadIcon} onClick={openFilePicker}>
                                {string.BULK_ADD_USERS_SELECT_FILE()}
                            </Button>
                        </VStack>
                    </VStack>
                </Dialog>

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
                                {req => (
                                    <UserListItem
                                        class="no-side-padding"
                                        user={new User(client, nonNull(req.user))}
                                        showId
                                        showGradeGroup
                                    />
                                )}
                            </For>
                            <Show when={importRequests().length > 10}>
                                <ListItem
                                    class="no-side-padding"
                                    headline={string.ADD_ELLIPSIS()}
                                    supportingText={string.USERS_COUNT({ count: importRequests().length - 10 })}
                                />
                            </Show>
                        </VStack>
                    </VStack>
                </Dialog>

                <Dialog
                    open={showErrors()}
                    onClose={() => {
                        setShowErrors(false)
                        setImportErrors([])
                    }}
                    headline={string.BULK_IMPORT_FAILED()}
                    actions={
                        <HStack gap={8} alignHorizontal="end" style={{ width: '100%' }}>
                            <Button
                                variant="text"
                                onClick={() => {
                                    setShowErrors(false)
                                    setImportErrors([])
                                    fileInput?.click()
                                }}
                            >
                                {string.RETRY()}
                            </Button>
                            <Button
                                onClick={() => {
                                    setShowErrors(false)
                                    setImportErrors([])
                                }}
                            >
                                {string.CLOSE()}
                            </Button>
                        </HStack>
                    }
                >
                    <VStack gap={8} style={{ 'max-width': '384px', 'max-height': '320px', 'overflow-y': 'auto' }}>
                        <ul style={{ margin: 0 }}>
                            <For each={importErrors()}>
                                {err => (
                                    <ListItem
                                        lines={4}
                                        class="no-side-padding"
                                        headline={<span class="m3-body-medium text-error">{formatError(err)}</span>}
                                        overline={
                                            err instanceof CSVParseError
                                                ? string.BULK_ADD_USERS_ERROR_SOURCE({
                                                      source: err.row,
                                                  })
                                                : undefined
                                        }
                                    />
                                )}
                            </For>
                        </ul>
                    </VStack>
                </Dialog>
            </Portal>
        </>
    )
}
