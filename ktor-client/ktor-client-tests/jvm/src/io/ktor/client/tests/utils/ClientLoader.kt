/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.coroutines.debug.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import org.junit.runner.*
import org.junit.runners.*
import java.util.*

/**
 * Helper interface to test client.
 */
@RunWith(Parameterized::class)
actual abstract class ClientLoader {

    @Parameterized.Parameter
    lateinit var engine: HttpClientEngineContainer

    @get:Rule
    open val timeout = CoroutinesTimeout.seconds(30)

    /**
     * Perform test against all clients from dependencies.
     */
    actual fun clientTests(
        skipPlatforms: List<String>,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ) {
        if ("jvm" in skipPlatforms) return
        clientTest(engine.factory, block)
    }

    actual fun dumpCoroutines() {
        DebugProbes.dumpCoroutines()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun engines(): List<HttpClientEngineContainer> = HttpClientEngineContainer::class.java.let {
            ServiceLoader.load(it, it.classLoader).toList()
        }
    }
}
