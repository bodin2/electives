import { mergeRefs } from '@solid-primitives/refs'
import { type ButtonProps, LoadingIndicator, Button as M3Button, mergeClasses } from 'm3-solid/src'
import { createEffect, createSignal, type JSX, Show, splitProps } from 'solid-js'
import { useI18n } from '../providers/I18nProvider'
import styles from './Button.module.css'

export function Button(
    props: Omit<Extract<ButtonProps, JSX.HTMLAttributes<HTMLButtonElement>>, 'variant' | 'onClick'> & {
        variant?: ButtonProps['variant'] | 'tonal-error'
        loading?: boolean
        onClick?: (e: Event) => unknown | Promise<unknown>
    },
) {
    const { string } = useI18n()
    const [local, others] = splitProps(props, ['variant', 'loading', 'onClick', 'icon', 'ref'])
    const [measuredSize, setMeasuredSize] = createSignal<{ width: string; height: string }>({
        width: '0px',
        height: '0px',
    })

    const [loading, setLoading] = createSignal(local.loading ?? false)
    const isLoadingStateControlled = () => local.loading !== undefined
    createEffect(() => {
        // Let the state be controlled from outside if a loading prop is provided
        if (local.loading !== undefined) setLoading(local.loading)
    })

    let button: HTMLButtonElement | undefined

    // Wrap onClick to prevent multiple clicks while loading, or completing an action
    const wrappedOnClick: JSX.EventHandlerUnion<HTMLButtonElement, MouseEvent> = async e => {
        if (loading() || !button) return

        const rect = button.getBoundingClientRect()
        const style = getComputedStyle(button)

        setMeasuredSize({
            width: `calc(${rect.width}px - ${style.paddingLeft} - ${style.paddingRight})`,
            height: `calc(${rect.height}px - ${style.paddingTop} - ${style.paddingBottom})`,
        })

        const onClick = local.onClick
        const ret = onClick?.(e)

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
            ref={mergeRefs(local.ref, el => (button = el))}
            icon={loading() ? undefined : local.icon}
            onClick={wrappedOnClick}
            class={mergeClasses(
                styles[wrappedVariant()],
                others.class,
                local.variant === 'tonal-error' && styles.tonalError,
            )}
            variant={wrappedVariant()}
        >
            <Show
                when={!loading()}
                fallback={
                    <>
                        <LoadingIndicator aria-label={string.LOADING()} class={styles.loading} />
                        <div style={measuredSize()} />
                    </>
                }
            >
                {props.children}
            </Show>
        </M3Button>
    )
}
