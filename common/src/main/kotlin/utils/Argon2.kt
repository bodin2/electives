package th.ac.bodin2.electives.utils

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.time.Duration

class Argon2(
    val memory: Long = DEFAULT_MEMORY,
    val iterations: Int,
) {
    val memoryInKiB = (memory / 1024).toInt()

    fun hash(password: CharArray): String {
        return argon2.hash(iterations, memoryInKiB, CORES, password)
    }

    fun verify(hash: String, password: CharArray): Boolean {
        return argon2.verify(hash, password)
    }

    /**
     * Suspending [hash] that runs on a bounded CPU dispatcher.
     */
    suspend fun hashAsync(password: CharArray): String = withContext(cpuDispatcher) { hash(password) }

    /**
     * Suspending [verify] that runs on the bounded CPU dispatcher.
     */
    suspend fun verifyAsync(hash: String, password: CharArray): Boolean =
        withContext(cpuDispatcher) { verify(hash, password) }

    companion object {
        private val CORES = Runtime.getRuntime().availableProcessors()
        val DEFAULT_MEMORY = 64.MiB

        /**
         * Bounds concurrent Argon2 computations to the number of available cores so a surge of
         * password hashing cannot consume all CPU and starve other (e.g. read) traffic.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val cpuDispatcher = Dispatchers.IO.limitedParallelism(CORES)

        private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

        fun findIterations(maxTime: Duration, memory: Long = DEFAULT_MEMORY): Int {
            return Argon2Helper.findIterations(argon2, maxTime.inWholeMilliseconds, (memory / 1024).toInt(), CORES)
        }
    }
}