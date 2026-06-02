import { type Accessor, createContext, useContext } from 'solid-js'

/**
 * Shell-owned UI state for the TopAppBar and focus management.
 */
export interface ShellChrome {
    navOpen: Accessor<boolean>
    setNavOpen: (v: boolean) => void
    modalNav: Accessor<boolean>
    topAppBarElevated: Accessor<boolean>
    setTopAppBarElevated: (v: boolean) => void
    /** Used as `!inert` on the main content. */
    focusable: Accessor<boolean>
}

const DEFAULT_CHROME: ShellChrome = {
    navOpen: () => true,
    setNavOpen: () => {},
    modalNav: () => false,
    topAppBarElevated: () => false,
    setTopAppBarElevated: () => {},
    focusable: () => true,
}

const ShellChromeContext = createContext<ShellChrome>(DEFAULT_CHROME)

export const ShellChromeProvider = ShellChromeContext.Provider

export const useShellChrome = () => useContext(ShellChromeContext)
