package io.ktor.client.tests.cio

import io.ktor.client.cio.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import java.util.concurrent.atomic.*
import kotlin.test.*

const val TEST_SIZE = 100
const val FAIL_TIMEOUT = 100

class SemaphoreTest {

    @Test
    fun passTest() = runBlocking {
        val semaphore = Semaphore(1)

        semaphore.enter()
        val task = launch {
            semaphore.enter()
        }

        assertFailsWith<TimeoutCancellationException> {
            runBlocking {
                withTimeout(100) {
                    task.join()
                }
            }
        }

        semaphore.leave()
        task.join()
    }

    @Test
    fun emptyTest() = runBlocking {
        val semaphore = Semaphore(1)
        val completed = AtomicInteger()

        semaphore.enter()
        val jobs = List(TEST_SIZE) {
            launch {
                semaphore.enter()
                completed.incrementAndGet()
            }
        }

        repeat(TEST_SIZE) {
            val prev = completed.get()
            semaphore.leave()
            while (true) {
                if (completed.get() == prev + 1) break
            }
        }

        jobs.forEach {
            it.join()
        }
    }

    @Test
    fun borderTest() = runBlocking {
        val semaphore = Semaphore(10)
        repeat(10) {
            semaphore.enter()
        }

        val job = launch {
            semaphore.enter()
        }

        repeat(TEST_SIZE) {
            assertFails {
                runBlocking {
                    withTimeout(FAIL_TIMEOUT) {
                        job.join()
                    }
                }
            }
        }

    }
}
