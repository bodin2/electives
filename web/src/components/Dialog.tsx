import { Dialog as M3Dialog, type DialogProps as M3DialogProps } from 'm3-solid/src'
import { createRenderEffect, createSignal, type ParentComponent, Show, splitProps } from 'solid-js'
import { Portal } from 'solid-js/web'
import styles from './Dialog.module.css'

export type DialogProps = Omit<M3DialogProps, 'open' | 'onOpenChange'> & {
    open: boolean
    onOpen?: () => void
    onClose?: () => void
}

export const Dialog: ParentComponent<DialogProps> = props => {
    const [local, others] = splitProps(props, ['open', 'onOpen', 'onClose', 'backdropProps', 'children'])
    const [internalOpen, setInternalOpen] = createSignal(false)
    const [shouldMount, setShouldMount] = createSignal(false)

    createRenderEffect(() => {
        if (local.open) {
            setShouldMount(true)
            setInternalOpen(true)
        } else {
            setInternalOpen(false)
        }
    })

    const handleOpenChange = (open: boolean) => {
        if (open) {
            local.onOpen?.()
        } else {
            setShouldMount(false)
            local.onClose?.()
        }
    }

    return (
        <Show when={shouldMount()}>
            <Portal>
                <M3Dialog
                    {...others}
                    open={internalOpen()}
                    onOpenChange={handleOpenChange}
                    backdropProps={{
                        class: styles.backdrop,
                        ...local.backdropProps,
                    }}
                >
                    {local.children}
                </M3Dialog>
            </Portal>
        </Show>
    )
}
