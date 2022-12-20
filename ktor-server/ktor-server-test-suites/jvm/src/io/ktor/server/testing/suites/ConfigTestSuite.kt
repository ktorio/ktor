/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import com.typesafe.config.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.*
import kotlin.system.*

var count = 0

abstract class ConfigTestSuite(
    val engine: ApplicationEngineFactory<*, *>,
    val configuration: () -> BaseApplicationEngine.Configuration
) {

    @Test
    fun testStartOnceWhenException() {
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
    fun testStartsOnceWithCapture() {
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
    fun testFastStop() = runBlocking {
        val server = embeddedServer(engine) {
        }

        val time = measureTimeMillis {
            server.stop(0, 100, TimeUnit.SECONDS)
        }

        assertTrue("Stop time is $time", time < 100)
    }

    @Test
    fun testCommonEngineConfiguration() {
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                    ktor {
                        deployment {
                            shutdownGracePeriod: 2000,
                            shutdownTimeout: 6000
                        }
                    }
                """.trimIndent()
            )
        )

        val configuration = configuration().apply { loadCommonConfiguration(config.config("ktor.deployment")) }
        assertEquals(2000, configuration.shutdownGracePeriodMillis)
        assertEquals(6000, configuration.shutdownTimeoutMillis)
    }
}
