import { useEnrollmentCounts } from '../providers/EnrollmentCountsProvider'
import type { Accessor } from 'solid-js'
import type { Elective, Subject } from '../api'

export default function useSubjectFull(subject: Accessor<Subject>, elective: Accessor<Elective>) {
    const counts = useEnrollmentCounts()

    const isFull = () => {
        const s = subject()
        const e = elective()

        const count = counts.getCount(e.id, s.id)
        return (count ?? 0) >= s.capacity
    }

    return isFull
}
