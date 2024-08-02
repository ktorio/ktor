/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationEventTest {
    @Test
    fun testApplicationStopPreparingEvent() {
        var c = 0

        withTestApplication {
            server.monitor.subscribe(ServerStopPreparing) {
                c += 1
            }
        }
        assertEquals(1, c)

        withTestApplication {
            server.monitor.subscribe(ServerStopPreparing) {
                c += 2
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun testApplicationStoppingEvent() {
        var c = 0

        withTestApplication {
            server.monitor.subscribe(ServerStopping) {
                c += 1
            }
        }
        assertEquals(1, c)

        withTestApplication {
            server.monitor.subscribe(ServerStopping) {
                c += 2
            }
        }
        assertEquals(3, c)
    }

    @Test
    fun testApplicationStopPreparingEventUsingWithApplication() {
        var c = 0

        withApplication {
            server.monitor.subscribe(ServerStopPreparing) {
                c += 1
            }
        }
        assertEquals(1, c)

        withApplication {
            server.monitor.subscribe(ServerStopPreparing) {
                c += 2
            }
        }
        assertEquals(3, c)
    }
}
