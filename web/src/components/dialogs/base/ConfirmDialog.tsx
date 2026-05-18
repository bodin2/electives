import { type ParentComponent, splitProps } from 'solid-js'
import { useI18n } from '~/providers/I18nProvider'
import { Button } from '../../Button'
import { Dialog, type DialogProps } from '../../Dialog'

export type ConfirmDialogProps = Omit<DialogProps, 'actions' | 'onClose'> & {
    onCancel?: () => unknown
    onConfirm: () => unknown
    confirmDisabled?: boolean
    confirmText?: string
    cancelText?: string
    variant?: 'default' | 'danger'
}

export const ConfirmDialog: ParentComponent<ConfirmDialogProps> = props => {
    const [local, dialogProps] = splitProps(props, [
        'onCancel',
        'onConfirm',
        'confirmDisabled',
        'confirmText',
        'cancelText',
        'children',
        'variant',
    ])
    const { string } = useI18n()

    return (
        <Dialog
            {...dialogProps}
            onClose={local.onCancel}
            actions={
                <form method="dialog" style={{ display: 'contents' }}>
                    <Button variant="text" onClick={() => local.onCancel?.()}>
                        {local.cancelText ?? string.CANCEL()}
                    </Button>
                    <Button
                        variant={local.variant === 'danger' ? 'tonal-error' : 'text'}
                        disabled={local.confirmDisabled}
                        onClick={() => local.onConfirm()}
                    >
                        {local.confirmText ?? string.CONFIRM()}
                    </Button>
                </form>
            }
        >
            {local.children}
        </Dialog>
    )
}
