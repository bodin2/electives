package th.ac.bodin2.electives.utils

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Helper
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val CORES = Runtime.getRuntime().availableProcessors()

object Argon2 {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    // Default: 64MB
    var MEMORY by Delegates.notNull<Int>()
    var ITERATIONS by Delegates.notNull<Int>()

    const val DEFAULT_MEMORY = 65536
    val DEFAULT_TIME = 500.milliseconds

    fun init(memory: Int = DEFAULT_MEMORY, iterations: Int = findIterations(DEFAULT_TIME.inWholeMilliseconds)) {
        MEMORY = memory
        ITERATIONS = iterations
    }

    fun init(memory: Int = DEFAULT_MEMORY, maxTime: Duration = DEFAULT_TIME) {
        MEMORY = memory
        ITERATIONS = findIterations(maxTime.inWholeMilliseconds)
    }

    fun hash(password: CharArray): String {
        return argon2.hash(ITERATIONS, MEMORY, CORES, password)
    }

    fun verify(hash: ByteArray, password: CharArray): Boolean {
        return argon2.verify(hash.decodeToString(), password)
    }

    fun findIterations(avgTimeMillis: Long): Int {
        return Argon2Helper.findIterations(argon2, avgTimeMillis, MEMORY, CORES)
    }
}