import Logger from '@bodin2/electives-common/Logger'
import * as i18n from '@solid-primitives/i18n'
import {
    createContext,
    createEffect,
    createResource,
    createSignal,
    onMount,
    type ParentComponent,
    useContext,
} from 'solid-js'
import { createStore } from 'solid-js/store'
import type Lang from '../i18n/th.json'

const log = new Logger('I18nProvider')

export type Dict = typeof Lang
export type Locale = 'en' | 'th'
export interface I18nApi {
    ready: boolean
    locale: () => Locale
    setLocale: (l: Locale) => void
    string: i18n.ChainedTranslator<Dict, string>
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

    let fetchAttempts = 0

    onMount(() => {
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

    createEffect(() => {
        if (dict.error) {
            log.error(`Failed to load i18n dictionary (attempt ${fetchAttempts}):`, dict.error)

            if (++fetchAttempts > 3) {
                log.warn('Max i18n fetch attempts reached, giving up')
                setValue({
                    ready: true,
                    string: new Proxy({}, { get: (_, key) => () => key }) as I18nApi['string'],
                })
                return
            }

            mutateDict.refetch()
        }

        if (dict.latest) {
            fetchAttempts = 0

            log.info('Loaded i18n dictionary for locale:', locale())
            setValue({ ready: true, string: i18n.chainedTranslator(dict.latest, tr) })
        }
    })

    const [dict, mutateDict] = createResource(locale, fetchDictionary)
    const tr = i18n.translator(dict, i18n.resolveTemplate) as i18n.Translator<Dict, string>

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

export function useI18n() {
    const ctx = useContext(I18nContext)
    if (!ctx) throw new Error('useI18n must be used within <I18nProvider>')
    return ctx
}
