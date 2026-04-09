package th.ac.bodin2.electives.utils

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Helper
import kotlin.time.Duration

class Argon2(
    val memory: Int = DEFAULT_MEMORY,
    val iterations: Int,
) {
    val memoryInKiB = memory / 1024

    fun hash(password: CharArray): String {
        return argon2.hash(iterations, memoryInKiB, CORES, password)
    }

    fun verify(hash: String, password: CharArray): Boolean {
        return argon2.verify(hash, password)
    }

    companion object {
        private val CORES = Runtime.getRuntime().availableProcessors()
        val DEFAULT_MEMORY = 64.MiB.toInt()

        private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

        fun findIterations(maxTime: Duration, memory: Int = DEFAULT_MEMORY): Int {
            return Argon2Helper.findIterations(argon2, maxTime.inWholeMilliseconds, memory / 1024, CORES)
        }
    }
}