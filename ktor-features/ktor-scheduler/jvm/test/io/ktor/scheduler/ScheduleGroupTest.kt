/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

@InternalCoroutinesApi
class ScheduleGroupTest {

    @Test
    fun scheduleTickerTest() {
        val context = TestTimeProvidingCoroutineContext(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0))

        with(ScheduleGroup(context, emptyList())) {
            val now = runBlocking {
                context.scheduleTicker(repeat = 1) {
                    assertEquals(this, LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0))
                    LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 1)
                }.receive()
            }
            assertEquals(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 1), now)
            assertEquals(context.now(), now)
        }
    }
}
