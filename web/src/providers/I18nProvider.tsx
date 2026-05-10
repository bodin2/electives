import Logger from '@bodin2/electives-common/Logger'
import * as i18n from '@solid-primitives/i18n'
import { createQuery, keepPreviousData } from '@tanstack/solid-query'
import {
    createContext,
    createEffect,
    createRenderEffect,
    createSignal,
    type JSXElement,
    on,
    type ParentComponent,
    useContext,
} from 'solid-js'
import { createStore } from 'solid-js/store'
import { nonNull } from '../utils'
import type { BaseRecordDict, Resolved } from '@solid-primitives/i18n'
import type Lang from '../i18n/th.json'

const log = new Logger('I18nProvider')

export type Dict = typeof Lang
export type Locale = 'en' | 'th'
export interface I18nApi {
    ready: boolean
    locale: () => Locale
    setLocale: (l: Locale) => void
    string: ChainedTranslatorWithJSX<Dict, string>
    t: i18n.Translator<Dict, string>
}

const I18nContext = createContext<I18nApi>()

const loaders = import.meta.glob('../i18n/*.json')

log.info(
    'Available i18n locales:',
    Object.keys(loaders).map(path => path.split(/\/|\./).at(-2)),
)

async function fetchDictionary(locale: Locale) {
    let loader = loaders[`../i18n/${locale}.json`]
    if (!loader) {
        loader = loaders['../i18n/en.json']
        log.warn(`No i18n dictionary found for locale '${locale}', falling back to 'en'`)
    }
    const mod = await loader()
    return i18n.flatten(mod as Dict)
}

const I18nProvider: ParentComponent = props => {
    const [locale, setLocale] = createSignal<Locale>('en')

    createRenderEffect(() => {
        const localStored = localStorage.getItem('locale') as Locale | null
        if (localStored) {
            log.info('Found stored locale preference:', localStored)
            return setLocale(localStored)
        }

        // TODO: Maybe use a more robust way to detect language
        const browserLang = navigator.language
        if (browserLang.startsWith('th')) setLocale('th')
        else setLocale('en')
    })

    createEffect(
        () => {
            log.info('Setting document language to:', locale())
            document.documentElement.lang = locale()
            localStorage.setItem('locale', locale())
        },
        { defer: true },
    )

    const dictQuery = createQuery(() => ({
        queryKey: ['i18n', locale()] as const,
        queryFn: () => fetchDictionary(locale()),
        staleTime: Number.POSITIVE_INFINITY,
        gcTime: Number.POSITIVE_INFINITY,
        placeholderData: keepPreviousData,
        retry: 3,
    }))
    const tr = i18n.translator(() => dictQuery.data, resolveTemplateWithJSX) as i18n.Translator<Dict, string>

    createEffect(
        on(
            () => [dictQuery.data, dictQuery.error, dictQuery.failureCount] as const,
            ([data, error, failureCount]) => {
                if (error && failureCount >= 3) {
                    log.error('Failed to load i18n dictionary after retries:', error)
                    log.warn('Max i18n fetch attempts reached, giving up')
                    setValue({
                        ready: true,
                        string: new Proxy({}, { get: (_, key) => () => key }) as I18nApi['string'],
                    })
                    return
                }

                if (data) {
                    log.info('Loaded i18n dictionary for locale:', locale())
                    setValue({
                        ready: true,
                        string: i18n.chainedTranslator(data, tr) as ChainedTranslatorWithJSX<Dict, string>,
                    })
                }
            },
        ),
    )

    const [value, setValue] = createStore<I18nApi>({
        ready: false,
        locale,
        setLocale,
        string: undefined as unknown as I18nApi['string'],
        t: tr,
    })

    return <I18nContext.Provider value={value}>{props.children}</I18nContext.Provider>
}

export default I18nProvider

export const useI18n = () => nonNull(useContext(I18nContext), 'useI18n must be used within <I18nProvider>')

// Slightly modified version of:
// https://github.com/solidjs-community/solid-primitives/issues/715#issuecomment-3764040195
export function resolveTemplateWithJSX(
    template: string,
    args?: Record<string, string | JSXElement>,
): string | JSXElement | JSXElement[] {
    if (!args) return template

    const regex = /\{\{\s*(\w+)\s*\}\}/g
    const parts: (string | JSXElement)[] = []
    let lastIndex = 0
    let match: RegExpExecArray | null
    let hasJSXElement = false

    // biome-ignore lint/suspicious/noAssignInExpressions: It's all good
    while ((match = regex.exec(template)) !== null) {
        if (match.index > lastIndex) {
            parts.push(template.slice(lastIndex, match.index))
        }

        const key = match[1]
        const value = args[key]

        if (typeof value !== 'string' && typeof value !== 'number' && typeof value !== 'boolean') {
            hasJSXElement = true
        }

        parts.push(value ?? match[0])
        lastIndex = regex.lastIndex
    }

    if (lastIndex < template.length) {
        parts.push(template.slice(lastIndex))
    }

    return hasJSXElement ? parts : parts.join('')
}

// export type TranslatorWithJSX<T extends BaseRecordDict, O = string> = <K extends keyof T>(path: K, ...args: [args?: Record<string, string | number | boolean | JSXElement>]) => Resolved<T[K], O>;
export type ChainedTranslatorWithJSX<T extends BaseRecordDict, O = string> = {
    readonly [K in keyof T]: T[K] extends BaseRecordDict ? ChainedTranslatorWithJSX<T[K], O> : ResolverWithJSX<T[K], O>
}

export type ResolverWithJSX<T, O = string> = (
    args?: Record<string, string | number | boolean | JSXElement>,
) => Resolved<T, O>
