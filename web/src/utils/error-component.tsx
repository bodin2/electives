import type { ErrorComponentProps, ErrorRouteComponent } from '@tanstack/solid-router'
import type { Component } from 'solid-js'

export function catchErrors(
    // biome-ignore lint/complexity/noBannedTypes: Right side of instanceof should be Function
    ...errors: Array<[clazz: Function, Component: Component<ErrorComponentProps>]>
): ErrorRouteComponent {
    return function CatchError(props) {
        const [, Comp] = errors.find(([clazz]) => props.error instanceof clazz) ?? []
        if (!Comp) throw props.error
        return <Comp {...props} />
    }
}
