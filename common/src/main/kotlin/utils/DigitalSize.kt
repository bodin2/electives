package th.ac.bodin2.electives.utils

inline val Int.KiB: Long get() = this.toLong() * 1024
inline val Int.MiB: Long get() = this.toLong() * 1024 * 1024
