import AdminIcon from '@iconify-icons/mdi/administrator'
import ClassIcon from '@iconify-icons/mdi/class'
import SettingsIcon from '@iconify-icons/mdi/cog'
import PeopleIcon from '@iconify-icons/mdi/people'
import TeamIcon from '@iconify-icons/mdi/people-group'
import TeacherIcon from '@iconify-icons/mdi/teacher'
import TicketIcon from '@iconify-icons/mdi/ticket'
import { createFileRoute, Link, Outlet } from '@tanstack/solid-router'
import {
    mergeClasses,
    NavigationRail,
    NavigationRailItem,
    type NavigationRailItemProps,
    NavigationRailToggle,
} from 'm3-solid'
import { createEffect, createRenderEffect, createSignal, on, onCleanup, onMount, splitProps } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../components/Button'
import LogOutButton from '../../components/buttons/LogOutButton'
import SettingsDialog from '../../components/dialogs/SettingsDialog'
import { PageTopAppBar } from '../../components/PageTopAppBar'
import { HStack, VStack } from '../../components/Stack'
import {
    BaseSubjectDisplayContext,
    SubjectDisplayContextProvider,
} from '../../components/subjects/SubjectDisplayContext'
import { UserDisplayContextProvider, useUserDisplayContext } from '../../components/users/UserDisplayContext'
import { useI18n } from '../../providers/I18nProvider'
import { usePageData } from '../../providers/PageProvider'
import ScrollDataProvider from '../../providers/ScrollDataProvider'
import styles from './manage.module.css'
import type { RoutePath } from '../../main'

export const Route = createFileRoute('/_adminAuthenticated/manage')({
    component: RouteComponent,
})

function RouteComponent() {
    const { string } = useI18n()
    const [navOpen, setNavOpen] = createSignal(true)
    const [settingsOpen, setSettingsOpen] = createSignal(false)
    const pageData = usePageData()
    const [containerRef, setContainerRef] = createSignal<HTMLDivElement | undefined>()
    const userDisplayContext = useUserDisplayContext()
    const [modalNav, setModalNav] = createSignal(false)

    const NavMenuToggle = () => (
        <div class={mergeClasses(styles.toggleContainer, navOpen() && styles.open, modalNav() && styles.modalNav)}>
            <NavigationRailToggle onChange={setNavOpen} open={navOpen()} mode="inline" />
        </div>
    )

    const AdminTrailing = () => {
        const { string } = useI18n()
        return (
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
    }

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
                pageData.setTrailing(AdminTrailing)

                onCleanup(() => {
                    pageData.setTopAppBarElevated(prevElevated)
                    pageData.setAllowBacking(prevAllowBacking)
                    pageData.setLeading(prevLeading)
                    pageData.setTrailing(prevTrailing)
                })
            },
        ),
    )

    createEffect(() => {
        if (!navOpen() && !modalNav()) return
        pageData.setTopAppBarElevated(navOpen())
    })

    createEffect(() => {
        if (modalNav()) pageData.setFocusable(!navOpen())
        else pageData.setFocusable(true)
    })

    createEffect(() => {
        setNavOpen(!modalNav())
    })

    return (
        <ScrollDataProvider container={containerRef()}>
            <PageTopAppBar elevated={pageData.topAppBarElevated} />
            <HStack id="admin-app" grow gap={0}>
                <div class={styles.navContainer}>
                    <NavigationRail
                        class={styles.navRail}
                        modal={modalNav()}
                        collapse={modalNav() ? 'full' : 'normal'}
                        alignment="top"
                        open={navOpen()}
                        onChange={setNavOpen}
                        fill
                    >
                        <LinkNavigationRailItem icon={AdminIcon} label={string.ADMIN_DASHBOARD()} to="/manage" exact />
                        <Separator />
                        <LinkNavigationRailItem icon={PeopleIcon} label={string.STUDENTS()} to="/manage/students" />
                        <LinkNavigationRailItem icon={TeacherIcon} label={string.TEACHERS()} to="/manage/teachers" />
                        <LinkNavigationRailItem icon={TeamIcon} label={string.GROUPS()} to="/manage/groups" />
                        <Separator />
                        <LinkNavigationRailItem
                            icon={TicketIcon}
                            label={string.ENROLLMENTS()}
                            to="/manage/enrollments"
                        />
                        <LinkNavigationRailItem icon={ClassIcon} label={string.SUBJECTS()} to="/manage/subjects" />
                    </NavigationRail>
                </div>
                <VStack tabindex="-1" grow class={styles.outer}>
                    <VStack
                        ref={setContainerRef}
                        tabindex="-1"
                        grow
                        class={mergeClasses(styles.inner, modalNav() && styles.modalNav)}
                        gap={0}
                    >
                        <SubjectDisplayContextProvider
                            value={{
                                editable: true,
                                createLinkProps: BaseSubjectDisplayContext.createLinkProps,
                                editLinkProps: BaseSubjectDisplayContext.editLinkProps,
                                viewLinkProps: (enrollmentId, subjectId, tab) => ({
                                    to: '/manage/subjects/$subjectId',
                                    params: { subjectId },
                                    search: { enrollment_id: enrollmentId, tab },
                                }),
                            }}
                        >
                            <UserDisplayContextProvider
                                value={{
                                    ...userDisplayContext,
                                    editable: true,
                                    createLinkProps: type => ({
                                        to: '/manage/users/$userId',
                                        params: { userId: 'new' },
                                        search: { type },
                                    }),
                                    editLinkProps: userId => ({
                                        to: '/manage/users/$userId',
                                        params: { userId },
                                    }),
                                    viewLinkProps: userId => ({
                                        to: '/manage/users/$userId',
                                        params: { userId },
                                    }),
                                }}
                            >
                                <Outlet />
                            </UserDisplayContextProvider>
                        </SubjectDisplayContextProvider>
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

function LinkNavigationRailItem(props: NavigationRailItemProps & { to: RoutePath; exact?: boolean }) {
    const [local, others] = splitProps(props, ['to', 'exact'])

    return (
        <Link to={local.to} style={{ display: 'contents' }} activeOptions={{ exact: local.exact }}>
            {state => <NavigationRailItem {...others} active={state.isActive} />}
        </Link>
    )
}
