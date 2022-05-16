/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.coroutines.*
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
actual abstract class ClientLoader actual constructor(val timeoutSeconds: Int) {

    @Parameterized.Parameter
    lateinit var engine: HttpClientEngineContainer

    @get:Rule
    open val timeout: CoroutinesTimeout = CoroutinesTimeout.seconds(timeoutSeconds)

    /**
     * Perform test against all clients from dependencies.
     */
    actual fun clientTests(
        skipEngines: List<String>,
        onlyWithEngine: String?,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ) {
        val locale = Locale.getDefault()
        val engineName = engine.toString().lowercase(locale)
        for (skipEngine in skipEngines) {
            val skipEngineArray = skipEngine.lowercase(locale).split(":")

            val (platform, skipEngineName) = when (skipEngineArray.size) {
                2 -> skipEngineArray[0] to skipEngineArray[1]
                1 -> "*" to skipEngineArray[0]
                else -> throw IllegalStateException("Wrong skip engine format, expected 'engine' or 'platform:engine'")
            }

            val platformShouldBeSkipped = "*" == platform || OS_NAME == platform
            val engineShouldBeSkipped = "*" == skipEngineName || engineName == skipEngineName

            if (platformShouldBeSkipped && engineShouldBeSkipped) {
                return
            }

            if (onlyWithEngine != null && engineName != onlyWithEngine) {
                return
            }
        }

        val enginesToSkip = skipEngines.map { it.lowercase(locale) }
        if (engineName in enginesToSkip) return

        testWithEngine(engine.factory, this, timeoutSeconds * 1000L, block)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    actual fun dumpCoroutines() {
        DebugProbes.dumpCoroutines()
    }

    /**
     * Issues to fix before unlock:
     * 1. Pinger & Ponger in ws
     * 2. Nonce generator
     */
    // @After
    @OptIn(ExperimentalCoroutinesApi::class)
    fun waitForAllCoroutines() {
        check(DebugProbes.isInstalled) {
            "Debug probes isn't installed."
        }

        val info = DebugProbes.dumpCoroutinesInfo()

        if (info.isEmpty()) {
            return
        }

        val message = buildString {
            appendLine("Test failed. There are running coroutines")
            appendLine(info.dump())
        }

        error(message)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun engines(): List<HttpClientEngineContainer> = HttpClientEngineContainer::class.java.let {
            ServiceLoader.load(it, it.classLoader).toList()
        }
    }
}

private val OS_NAME: String
    get() {
        val os = System.getProperty("os.name", "unknown").lowercase(Locale.getDefault())
        return when {
            os.contains("win") -> "win"
            os.contains("mac") -> "mac"
            os.contains("nux") -> "unix"
            else -> "unknown"
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun List<CoroutineInfo>.dump(): String = buildString {
    this@dump.forEach { info ->
        appendLine("Coroutine: $info")
        info.lastObservedStackTrace().forEach {
            appendLine("\t$it")
        }
    }
}
