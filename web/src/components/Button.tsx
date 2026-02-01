import { type ButtonProps, LoadingIndicator, Button as M3Button, mergeClasses } from 'm3-solid'
import { createEffect, createSignal, type JSX, Show, splitProps } from 'solid-js'
import { useI18n } from '../providers/I18nProvider'
import styles from './Button.module.css'

export function Button(
    props: Omit<Extract<ButtonProps, JSX.HTMLAttributes<HTMLButtonElement>>, 'variant'> & {
        variant?: ButtonProps['variant'] | 'tonal-error'
        loading?: boolean
        onClick: () => unknown | Promise<unknown>
    },
) {
    const { string } = useI18n()
    const [local, others] = splitProps(props, ['variant', 'loading', 'onClick', 'icon'])

    const [loading, setLoading] = createSignal(local.loading ?? false)
    const isLoadingStateControlled = () => local.loading !== undefined
    createEffect(() => {
        // Let the state be controlled from outside if a loading prop is provided
        if (local.loading !== undefined) setLoading(local.loading)
    })

    // Wrap onClick to prevent multiple clicks while loading, or completing an action
    const wrappedOnClick: JSX.EventHandlerUnion<HTMLButtonElement, MouseEvent> = async e => {
        if (loading()) return

        const onClick = local.onClick
        const ret = onClick(e)

        if (ret instanceof Promise) {
            const controlled = isLoadingStateControlled()
            if (!controlled) setLoading(true)
            ret.finally(() => {
                if (!controlled) setLoading(false)
            })
        }
    }

    const wrappedVariant = () => {
        switch (local.variant) {
            case 'tonal-error':
                return 'tonal'
            default:
                return local.variant ?? 'filled'
        }
    }

    return (
        <M3Button
            {...others}
            icon={loading() ? undefined : local.icon}
            onClick={wrappedOnClick}
            class={mergeClasses(styles[wrappedVariant()], others.class)}
            classList={{
                [styles.tonalError]: local.variant === 'tonal-error',
            }}
            variant={wrappedVariant()}
        >
            {/* TODO: Fix ripple in the lib, div makes sure the children (incl. the ripple) don't get fully overwritten by Solid */}
            <Show when={props.children || loading()}>
                <div style={{ display: 'contents', position: 'relative' }}>
                    <Show when={loading()}>
                        <LoadingIndicator aria-label={string.LOADING()} class={styles.loading} />
                    </Show>
                    <div style={{ display: 'flex', opacity: loading() ? 0 : 1 }} aria-hidden={loading()}>
                        {props.children}
                    </div>
                </div>
            </Show>
        </M3Button>
    )
}
