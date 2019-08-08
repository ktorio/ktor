/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import io.ktor.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.Test
import java.time.*
import java.util.concurrent.*
import kotlin.test.*

@InternalCoroutinesApi
class SchedulerTest {

    @Test
    fun everySecondRepeat10Test() {
        val context = TestTimeProvidingCoroutineContext(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0))
        val latch = CountDownLatch(10)
        var count = 0

        withApplication {
            with(application) {
                install(Scheduler)

                schedule(context) {
                    everySecond(repeat = 10) { now ->
                        count++
                        assertEquals(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, count), now)
                        latch.countDown()
                    }
                }
            }
        }

        latch.await()
        assertEquals(10, count)
    }

}
