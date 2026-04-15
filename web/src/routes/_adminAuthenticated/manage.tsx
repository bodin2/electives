import AdminIcon from '@iconify-icons/mdi/administrator'
import ClassIcon from '@iconify-icons/mdi/class'
import SettingsIcon from '@iconify-icons/mdi/cog'
import PeopleIcon from '@iconify-icons/mdi/people'
import TeamIcon from '@iconify-icons/mdi/people-group'
import TeacherIcon from '@iconify-icons/mdi/teacher'
import { createFileRoute, Link, Outlet } from '@tanstack/solid-router'
import {
    mergeClasses,
    NavigationRail,
    NavigationRailItem,
    type NavigationRailItemProps,
    NavigationRailToggle,
} from 'm3-solid'
import { createSignal, onCleanup, onMount, splitProps } from 'solid-js'
import { Portal } from 'solid-js/web'
import { Button } from '../../components/Button'
import LogOutButton from '../../components/buttons/LogOutButton'
import SettingsDialog from '../../components/dialogs/SettingsDialog'
import { HStack, VStack } from '../../components/Stack'
import { useI18n } from '../../providers/I18nProvider'
import { usePageData } from '../../providers/PageProvider'
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

    const NavMenuToggle = () => (
        <div class={mergeClasses(styles.toggleContainer, navOpen() && styles.open)}>
            <NavigationRailToggle onChange={setNavOpen} open={navOpen()} mode="inline" />
        </div>
    )

    const AdminTrailing = () => (
        <HStack>
            <LogOutButton />
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
        if (!pageData) return

        pageData.setTopAppBarElevated(true)
        pageData.setAllowBacking(false)

        const prevLeading = pageData.leading
        const prevTrailing = pageData.trailing
        pageData.setLeading(NavMenuToggle)
        pageData.setTrailing(AdminTrailing)

        onCleanup(() => {
            pageData.setTopAppBarElevated(false)
            pageData.setAllowBacking(true)
            pageData.setLeading(prevLeading)
            pageData.setTrailing(prevTrailing)
        })
    })

    return (
        <HStack grow align="stretch" gap={0} style={{ 'min-height': 0 }}>
            <div class={styles.navContainer}>
                <NavigationRail
                    style={{ 'padding-block': '16px' }}
                    collapse="no"
                    open={navOpen()}
                    onChange={setNavOpen}
                    fill
                >
                    <LinkNavigationRailItem icon={AdminIcon} label={string.ADMIN_DASHBOARD()} to="/manage" />
                    <Separator />
                    <LinkNavigationRailItem icon={PeopleIcon} label={string.STUDENTS()} to="/manage/students" />
                    <LinkNavigationRailItem icon={TeacherIcon} label={string.TEACHERS()} to="/manage/teachers" />
                    <LinkNavigationRailItem icon={TeamIcon} label={string.TEAMS()} to="/manage/teams" />
                    <Separator />
                    <LinkNavigationRailItem icon={ClassIcon} label={string.ELECTIVES()} to="/manage/electives" />
                    <LinkNavigationRailItem icon={ClassIcon} label={string.SUBJECTS()} to="/manage/subjects" />
                </NavigationRail>
            </div>
            <VStack tabindex="-1" grow class={styles.outer}>
                <VStack tabindex="-1" grow class={styles.inner} gap={0}>
                    <Outlet />
                </VStack>
            </VStack>
            <Portal>
                <SettingsDialog open={settingsOpen()} onClose={() => setSettingsOpen(false)} />
            </Portal>
        </HStack>
    )
}

function Separator() {
    return <hr class={styles.sep} />
}

function LinkNavigationRailItem(props: NavigationRailItemProps & { to: RoutePath }) {
    const [local, others] = splitProps(props, ['to'])

    return (
        <Link to={local.to} style={{ display: 'contents' }} activeOptions={{ exact: true }}>
            {state => <NavigationRailItem {...others} active={state.isActive} />}
        </Link>
    )
}
