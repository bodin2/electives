/// <reference types="vite/client" />

import type { JSX } from 'solid-js'

export type StyleRecordOnly<T extends object> = Omit<T, 'style'> & {
    style?: JSX.CSSProperties
}

declare module 'solid-js' {
    namespace JSX {
        interface LabelHTMLAttributes<_T> {
            for?: string
        }
    }
}
