/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.server.engine.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.*
import kotlin.system.*

public var count: Int = 0

public abstract class ConfigTestSuite(public val engine: ApplicationEngineFactory<*, *>) {

    @Test
    public fun testStartOnceWhenException() {
        // Please note that it's critical to not capture any variables inside the scope
        // It will disable autoreload and change behaviour
        val server = embeddedServer(engine) {
            count++
            error("Foo")
        }

        assertFailsWith<IllegalStateException> {
            server.start()
        }

        assertEquals(1, count)
        server.stop(1, 1, TimeUnit.SECONDS)
    }

    @Test
    public fun testStartsOnceWithCapture() {
        var counter = 0
        val server = embeddedServer(engine) {
            counter++
            error("Foo")
        }

        assertFailsWith<IllegalStateException> {
            server.start()
        }

        assertEquals(1, counter)
        server.stop(1, 1, TimeUnit.SECONDS)
    }

    @Test
    public fun testFastStop(): Unit = runBlocking {
        val server = embeddedServer(engine) {
        }

        val time = measureTimeMillis {
            server.stop(0, 100, TimeUnit.SECONDS)
        }

        assertTrue("Stop time is $time", time < 100)
    }
}
