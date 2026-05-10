import { useEnrollmentCounts } from '../providers/EnrollmentCountsProvider'
import type { Accessor } from 'solid-js'
import type { Enrollment, Subject } from '../api'

export default function useSubjectFull(subject: Accessor<Subject>, enrollment: Accessor<Enrollment>) {
    const counts = useEnrollmentCounts()

    const isFull = () => {
        const s = subject()
        const e = enrollment()

        const count = counts.getCount(e.id, s.id)
        return count >= s.capacity
    }

    return isFull
}
