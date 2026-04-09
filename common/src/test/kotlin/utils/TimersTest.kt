package th.ac.bodin2.electives.utils

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SetIntervalTest {
    @Test
    fun `executes multiple times`() = runBlocking {
        var counter = 0
        val job = setInterval(50.milliseconds) {
            counter++
        }

        delay(250.milliseconds)
        job.cancel()

        assertTrue(counter >= 4)
    }

    @Test
    fun `stops when canceled`() = runBlocking {
        var counter = 0
        val job = setInterval(50.milliseconds) {
            counter++
        }

        delay(150.milliseconds)
        job.cancel()
        val counterAfterCancel = counter

        delay(150.milliseconds)
        assertEquals(counterAfterCancel, counter)
    }

    @Test
    fun `zero duration executes every tick`() = runBlocking {
        var counter = 0
        val job = setInterval(0.milliseconds) {
            counter++
            if (counter >= 10) {
                throw CancellationException()
            }
        }

        delay(50.milliseconds)
        job.cancel()

        assertTrue(counter >= 10)
    }

    @Test
    fun `long duration doesn't execute initially`() = runBlocking {
        var counter = 0
        val job = setInterval(1.seconds) {
            counter++
        }

        delay(500.milliseconds)
        job.cancel()

        assertTrue(counter <= 1)
    }

    @Test
    fun `suspending action should block`() = runBlocking {
        var counter = 0
        val job = setInterval(50.milliseconds) {
            delay(500.milliseconds)
            counter++
        }

        delay(250.milliseconds)
        job.cancel()

        assertTrue(counter < 1)
    }

    @Test
    fun `setting does not block scope`() = runBlocking {
        var intervalExecuted = false

        val job = setInterval(100.milliseconds) {
            intervalExecuted = true
        }

        assertFalse(intervalExecuted)
        delay(150.milliseconds)
        job.cancel()

        assertTrue(intervalExecuted)
    }

    @Test
    fun `can run concurrently`() = runBlocking {
        var counter1 = 0
        var counter2 = 0

        val job1 = setInterval(50.milliseconds) {
            counter1++
        }

        val job2 = setInterval(75.milliseconds) {
            counter2++
        }

        delay(300.milliseconds)
        job1.cancel()
        job2.cancel()

        assertTrue(counter1 > counter2)
    }

    @Test
    fun `stops when scope is cancelled`() = runBlocking {
        var counter = 0
        val scope = CoroutineScope(Job())

        scope.setInterval(50.milliseconds) {
            counter++
        }

        delay(150.milliseconds)
        scope.cancel()
        val counterAfterCancel = counter

        delay(150.milliseconds)
        assertEquals(counterAfterCancel, counter)
    }

    @Test
    fun `works with exceptions handler`() = runBlocking {
        var counter = 0
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        val scope = CoroutineScope(Job() + exceptionHandler)

        val job = scope.setInterval(50.milliseconds) {
            counter++
            if (counter == 3) {
                throw RuntimeException("Test exception")
            }
        }

        delay(250.milliseconds)
        job.cancel()

        assertTrue(counter >= 3)
    }
}

