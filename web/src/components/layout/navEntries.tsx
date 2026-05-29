import AdminIcon from '@iconify-icons/mdi/administrator'
import ClassIcon from '@iconify-icons/mdi/class'
import PeopleIcon from '@iconify-icons/mdi/people'
import TeamIcon from '@iconify-icons/mdi/people-group'
import TeacherIcon from '@iconify-icons/mdi/teacher'
import TicketIcon from '@iconify-icons/mdi/ticket'
import { UserType } from '~/api'
import type { IconifyIcon } from '@iconify/types'
import type { RoutePath } from '~/main'
import type { ChainedTranslatorWithJSX, Dict } from '~/providers/I18nProvider'

export interface NavEntry {
    icon: IconifyIcon
    activeIcon?: IconifyIcon
    label: (string: ChainedTranslatorWithJSX<Dict, string>) => string
    to: RoutePath
    exact?: boolean
    isActive?: () => boolean
}

export type NavItem = NavEntry | null

export const STUDENT_NAV: NavItem[] = [
    { icon: TicketIcon, label: string => string.ENROLLMENTS(), to: '/', exact: true },
]

export const TEACHER_NAV: NavItem[] = [
    { icon: TicketIcon, label: string => string.ENROLLMENTS(), to: '/', exact: true },
]

/**
 * @param isOnUserRouteForType Predicate used to keep the Students/Teachers entries active
 */
export function getAdminNav(isOnUserRouteForType: (type: UserType) => boolean): NavItem[] {
    return [
        { icon: AdminIcon, label: string => string.ADMIN_DASHBOARD(), to: '/manage', exact: true },
        null,
        {
            icon: PeopleIcon,
            label: string => string.STUDENTS(),
            to: '/manage/students',
            isActive: () => isOnUserRouteForType(UserType.STUDENT),
        },
        {
            icon: TeacherIcon,
            label: string => string.TEACHERS(),
            to: '/manage/teachers',
            isActive: () => isOnUserRouteForType(UserType.TEACHER),
        },
        { icon: TeamIcon, label: string => string.GROUPS(), to: '/manage/groups' },
        null,
        { icon: TicketIcon, label: string => string.ENROLLMENTS(), to: '/manage/enrollments' },
        { icon: ClassIcon, label: string => string.SUBJECTS(), to: '/manage/subjects' },
    ]
}

export function getUserNav(type: UserType): NavItem[] {
    return type === UserType.TEACHER ? TEACHER_NAV : STUDENT_NAV
}
