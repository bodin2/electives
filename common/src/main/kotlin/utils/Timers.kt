package th.ac.bodin2.electives.utils

import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Runs the given [action] every [delay].
 *
 * This interval will stop running once the [CoroutineScope] is cancelled or completed.
 * ```
 * runBlocking {
 *   val job = coroutineScope.setInterval(1.seconds) {
 *     println("This will print every second")
 *   }
 *
 *   // Run for 5 seconds
 *   delay(5000L)
 * }
 * ```
 *
 * To create an interval that runs until explicitly stopped, use [GlobalScope] or another long-lived scope:
 * ```
 * lateinit var job: Job
 *
 * // Runs until the program is terminated
 * GlobalScope.launch {
 *   job = setInterval(2.seconds) {
 *     println("This will print every 2 seconds")
 *   }
 * }
 *
 * ...
 *
 * // To stop the interval
 * job.cancel()
 * ```
 */
fun CoroutineScope.setInterval(
    delay: Duration,
    action: suspend () -> Unit
): Job {
    return launch {
        while (isActive) {
            action()
            delay(delay)
        }
    }
}

/**
 * Ensures that the given [block] takes at least [minimumDelay] to complete.
 *
 * If the [block] completes in less time than [minimumDelay], this function will
 * suspend for the remaining time.
 *
 * Example:
 * ```
 * withMinimumDelay(2.seconds) {
 *   // Some quick operation
 * }
 * // This will take at least 2 seconds to complete
 * ```
 *
 * @param minimumDelay The minimum duration that the [block] should take.
 * @param block The suspend function to execute.
 */
suspend fun <T> withMinimumDelay(
    minimumDelay: Duration,
    block: suspend () -> T
): T {
    val start = TimeSource.Monotonic.markNow()

    try {
        return block()
    } finally {
        val elapsed = start.elapsedNow()

        val remainingDelay = minimumDelay - elapsed
        if (remainingDelay.isPositive()) {
            delay(remainingDelay)
        }
    }
}