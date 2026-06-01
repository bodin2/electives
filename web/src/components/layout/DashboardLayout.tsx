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
    createRenderEffect,
    createSignal,
    For,
    type JSXElement,
    on,
    onCleanup,
    onMount,
    Show,
    splitProps,
} from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '~/components/Button'
import LogOutButton from '~/components/buttons/LogOutButton'
import SettingsDialog from '~/components/dialogs/SettingsDialog'
import { PageTopAppBar } from '~/components/PageTopAppBar'
import { HStack, VStack } from '~/components/Stack'
import { useI18n } from '~/providers/I18nProvider'
import { usePageData } from '~/providers/PageProvider'
import ScrollDataProvider from '~/providers/ScrollDataProvider'
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
    const pageData = usePageData()

    return (
        <ScrollDataProvider>
            <PageTopAppBar elevated={pageData.topAppBarElevated} />
            {props.children}
        </ScrollDataProvider>
    )
}

function RailShell(props: { entries: NavItem[]; children: JSXElement }) {
    const { string } = useI18n()
    const [navOpen, setNavOpen] = createSignal(true)
    const [settingsOpen, setSettingsOpen] = createSignal(false)
    const pageData = usePageData()
    const [containerRef, setContainerRef] = createSignal<HTMLDivElement | undefined>()
    const [modalNav, setModalNav] = createSignal(false)

    const NavMenuToggle = () => (
        <div class={mergeClasses(styles.toggleContainer, navOpen() && styles.open, modalNav() && styles.modalNav)}>
            <NavigationRailToggle onChange={setNavOpen} open={navOpen()} mode="inline" />
        </div>
    )

    const TrailingActions = () => (
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

    onMount(() => {
        const mql = window.matchMedia('(max-width: 880px)')
        setModalNav(mql.matches)
        const listener = (e: MediaQueryListEvent) => setModalNav(e.matches)
        mql.addEventListener('change', listener)
        onCleanup(() => mql.removeEventListener('change', listener))
    })

    createRenderEffect(
        on(
            () => pageData,
            pageData => {
                const prevLeading = pageData.leading
                const prevTrailing = pageData.trailing
                const prevElevated = pageData.topAppBarElevated
                const prevAllowBacking = pageData.allowBacking

                pageData.setAllowBacking(false)
                pageData.setLeading(NavMenuToggle)
                pageData.setTrailing(TrailingActions)

                onCleanup(() => {
                    pageData.setTopAppBarElevated(prevElevated)
                    pageData.setAllowBacking(prevAllowBacking)
                    pageData.setLeading(prevLeading)
                    pageData.setTrailing(prevTrailing)
                })
            },
        ),
    )

    createRenderEffect(() => {
        if (!navOpen() && !modalNav()) return
        pageData.setTopAppBarElevated(navOpen())
    })

    createRenderEffect(() => {
        if (modalNav()) pageData.setFocusable(!navOpen())
        else pageData.setFocusable(true)
    })

    createRenderEffect(() => {
        setNavOpen(!modalNav())
    })

    return (
        <ScrollDataProvider container={containerRef()}>
            <PageTopAppBar elevated={pageData.topAppBarElevated} />
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
