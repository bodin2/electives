import SettingsIcon from '@iconify-icons/mdi/cog'
import { Link } from '@tanstack/solid-router'
import {
    mergeClasses,
    NavigationRail,
    NavigationRailItem,
    type NavigationRailItemProps,
    NavigationRailToggle,
} from 'm3-solid/src'
import {
    type Component,
    createMemo,
    createRenderEffect,
    createSignal,
    For,
    type JSXElement,
    onCleanup,
    onMount,
    Show,
    splitProps,
} from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '~/components/Button'
import LogOutButton from '~/components/buttons/LogOutButton'
import SettingsDialog from '~/components/dialogs/SettingsDialog'
import ShellTopAppBar from '~/components/ShellTopAppBar'
import { HStack, VStack } from '~/components/Stack'
import { useI18n } from '~/providers/I18nProvider'
import PageOptionsProvider from '~/providers/PageOptionsProvider'
import ScrollDataProvider from '~/providers/ScrollDataProvider'
import { type ShellChrome, ShellChromeProvider } from '~/providers/ShellChromeProvider'
import styles from './DashboardLayout.module.css'
import type { RoutePath } from '~/main'
import type { NavEntry, NavItem } from './navEntries'

interface DashboardLayoutProps {
    entries: NavItem[]
    children: JSXElement
}

export default function DashboardLayout(props: DashboardLayoutProps) {
    const visibleEntries = () => props.entries.filter((entry): entry is NavEntry => entry !== null)

    return (
        <Show when={visibleEntries().length > 1} fallback={<PlainShell>{props.children}</PlainShell>}>
            <RailShell entries={props.entries}>{props.children}</RailShell>
        </Show>
    )
}

function PlainShell(props: { children: JSXElement }) {
    const { string } = useI18n()
    const [settingsOpen, setSettingsOpen] = createSignal(false)

    const ChromeTrailing: Component = () => (
        <Button
            variant="text"
            aria-label={string.SETTINGS()}
            icon={SettingsIcon}
            iconType="only"
            onClick={() => setSettingsOpen(true)}
        />
    )

    return (
        <ShellChromeProvider value={makePlainShellChrome()}>
            <PageOptionsProvider>
                <ScrollDataProvider>
                    <ShellTopAppBar chromeTrailing={ChromeTrailing} />
                    {props.children}
                    <Portal>
                        <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
                    </Portal>
                </ScrollDataProvider>
            </PageOptionsProvider>
        </ShellChromeProvider>
    )
}

function makePlainShellChrome(): ShellChrome {
    const [elevated, setElevated] = createSignal(false)
    return {
        navOpen: () => true,
        setNavOpen: () => {},
        modalNav: () => false,
        topAppBarElevated: elevated,
        setTopAppBarElevated: setElevated,
        focusable: () => true,
    }
}

function RailShell(props: { entries: NavItem[]; children: JSXElement }) {
    const { string } = useI18n()
    const [navOpen, setNavOpen] = createSignal(true)
    const [settingsOpen, setSettingsOpen] = createSignal(false)
    const [containerRef, setContainerRef] = createSignal<HTMLDivElement | undefined>()
    const [modalNav, setModalNav] = createSignal(false)
    const [topAppBarElevated, setTopAppBarElevated] = createSignal(false)

    onMount(() => {
        const mql = window.matchMedia('(max-width: 880px)')
        setModalNav(mql.matches)
        const listener = (e: MediaQueryListEvent) => setModalNav(e.matches)
        mql.addEventListener('change', listener)
        onCleanup(() => mql.removeEventListener('change', listener))
    })

    createRenderEffect(() => {
        setNavOpen(!modalNav())
    })

    createRenderEffect(() => {
        if (!navOpen() && !modalNav()) return
        setTopAppBarElevated(navOpen())
    })

    const focusable = createMemo(() => (modalNav() ? !navOpen() : true))

    const chrome: ShellChrome = {
        navOpen,
        setNavOpen,
        modalNav,
        topAppBarElevated,
        setTopAppBarElevated,
        focusable,
    }

    const NavMenuToggle: Component = () => (
        <div class={mergeClasses(styles.toggleContainer, navOpen() && styles.open, modalNav() && styles.modalNav)}>
            <NavigationRailToggle onChange={setNavOpen} open={navOpen()} mode="inline" />
        </div>
    )

    const ChromeTrailing: Component = () => (
        <HStack>
            <LogOutButton iconType={modalNav() ? 'only' : 'left'} noText={modalNav()} />
            <Button
                variant="text"
                aria-label={string.SETTINGS()}
                icon={SettingsIcon}
                iconType="only"
                onClick={() => {
                    setSettingsOpen(true)
                }}
            />
        </HStack>
    )

    return (
        <ShellChromeProvider value={chrome}>
            <PageOptionsProvider>
                <ScrollDataProvider container={containerRef()}>
                    <ShellTopAppBar chromeLeading={NavMenuToggle} chromeTrailing={ChromeTrailing} />
                    <HStack id="dashboard-app" grow gap={0}>
                        <div class={styles.navContainer}>
                            <NavigationRail
                                class={styles.navRail}
                                modal={modalNav()}
                                collapse={modalNav() ? 'full' : 'normal'}
                                alignment="top"
                                open={navOpen()}
                                onChange={setNavOpen}
                                backdropProps={{ style: { 'z-index': 'var(--layer-nav-backdrop)' } }}
                                hideTop
                                fill
                            >
                                <For each={props.entries}>
                                    {item =>
                                        item === null ? (
                                            <Separator />
                                        ) : (
                                            <LinkNavigationRailItem
                                                icon={item.icon}
                                                activeIcon={item.activeIcon}
                                                label={item.label(string)}
                                                to={item.to}
                                                exact={item.exact}
                                                isActive={item.isActive}
                                            />
                                        )
                                    }
                                </For>
                            </NavigationRail>
                        </div>
                        <VStack grow class={styles.outer}>
                            <VStack
                                ref={setContainerRef}
                                grow
                                class={mergeClasses(styles.inner, modalNav() && styles.modalNav)}
                                gap={0}
                            >
                                {props.children}
                            </VStack>
                        </VStack>
                        <Portal>
                            <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
                        </Portal>
                    </HStack>
                </ScrollDataProvider>
            </PageOptionsProvider>
        </ShellChromeProvider>
    )
}

function Separator() {
    return <hr class={styles.sep} />
}

function LinkNavigationRailItem(
    props: NavigationRailItemProps & { to: RoutePath; exact?: boolean; isActive?: () => boolean },
) {
    const [local, others] = splitProps(props, ['to', 'exact'])

    return (
        <Link to={local.to} style={{ display: 'contents' }} activeOptions={{ exact: local.exact }}>
            {state => <NavigationRailItem {...others} active={state.isActive || props.isActive?.()} />}
        </Link>
    )
}
