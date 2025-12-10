package th.ac.bodin2.electives.utils

import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimersTest {
    @Test
    fun testSetIntervalExecutesMultipleTimes() = runBlocking {
        var counter = 0
        val job = setInterval(50.milliseconds) {
            counter++
        }

        delay(250)
        job.cancel()

        assertTrue(counter >= 4)
    }

    @Test
    fun testSetIntervalStopsWhenCancelled() = runBlocking {
        var counter = 0
        val job = setInterval(50.milliseconds) {
            counter++
        }

        delay(150)
        job.cancel()
        val counterAfterCancel = counter

        delay(150)
        assertEquals(counterAfterCancel, counter)
    }

    @Test
    fun testSetIntervalWithZeroDuration() = runBlocking {
        var counter = 0
        val job = setInterval(0.milliseconds) {
            counter++
            if (counter >= 10) {
                throw CancellationException()
            }
        }

        delay(50)
        job.cancel()

        assertTrue(counter >= 10)
    }

    @Test
    fun testSetIntervalWithLongDuration() = runBlocking {
        var counter = 0
        val job = setInterval(1.seconds) {
            counter++
        }

        delay(500)
        job.cancel()

        assertTrue(counter <= 1)
    }

    @Test
    fun testSetIntervalWithSuspendingAction() = runBlocking {
        var counter = 0
        val job = setInterval(50.milliseconds) {
            delay(10)
            counter++
        }

        delay(250)
        job.cancel()

        assertTrue(counter >= 3)
    }

    @Test
    fun testSetIntervalDoesNotBlockScope() = runBlocking {
        var intervalExecuted = false
        var afterIntervalExecuted: Boolean

        val job = setInterval(100.milliseconds) {
            intervalExecuted = true
        }

        afterIntervalExecuted = true
        delay(150)
        job.cancel()

        assertTrue(intervalExecuted)
        assertTrue(afterIntervalExecuted)
    }

    @Test
    fun testMultipleSetIntervals() = runBlocking {
        var counter1 = 0
        var counter2 = 0

        val job1 = setInterval(50.milliseconds) {
            counter1++
        }

        val job2 = setInterval(75.milliseconds) {
            counter2++
        }

        delay(300)
        job1.cancel()
        job2.cancel()

        assertTrue(counter1 > counter2)
    }

    @Test
    fun testSetIntervalStopsWithScopeCancel() = runBlocking {
        var counter = 0
        val scope = CoroutineScope(Job())

        scope.setInterval(50.milliseconds) {
            counter++
        }

        delay(150)
        scope.cancel()
        val counterAfterCancel = counter

        delay(150)
        assertEquals(counterAfterCancel, counter)
    }

    @Test
    fun testSetIntervalExceptionHandling() = runBlocking {
        var counter = 0
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        val scope = CoroutineScope(Job() + exceptionHandler)

        val job = scope.setInterval(50.milliseconds) {
            counter++
            if (counter == 3) {
                throw RuntimeException("Test exception")
            }
        }

        delay(250)
        job.cancel()

        assertTrue(counter >= 3)
    }
}

