package th.ac.bodin2.electives.utils

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Helper

private val CORES = Runtime.getRuntime().availableProcessors()

object Argon2 {
    private val argon2 = Argon2Factory.create()

    // Default: 64MB
    var MEMORY = 65536
    var ITERATIONS = Argon2Helper.findIterations(argon2, 500, MEMORY, CORES)

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