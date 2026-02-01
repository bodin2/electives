import { Dialog as M3Dialog, type DialogProps as M3DialogProps } from 'm3-solid'
import { createEffect, createSignal, Show, splitProps, type ParentComponent } from 'solid-js'
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

    createEffect(() => {
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
        </Show>
    )
}
