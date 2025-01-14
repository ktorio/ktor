/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.test.base

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.CoroutineInfo
import kotlinx.coroutines.debug.DebugProbes
import java.util.*

@OptIn(InternalAPI::class)
internal actual val enginesToTest: Iterable<HttpClientEngineFactory<HttpClientEngineConfig>> by lazy {
    val enginesIterator = loadServicesAsSequence<HttpClientEngineContainer>().iterator()

    buildList {
        while (enginesIterator.hasNext()) {
            try {
                add(enginesIterator.next().factory)
            } catch (_: UnsupportedClassVersionError) {
                // Ignore clients compiled against newer JVM
            }
        }
    }
}

internal actual val platformName: String by lazy {
    val os = System.getProperty("os.name", "unknown").lowercase(Locale.getDefault())
    "jvm/" + when {
        os.contains("win") -> "win"
        os.contains("mac") -> "mac"
        os.contains("nux") -> "unix"
        else -> "unknown"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun platformDumpCoroutines() {
    DebugProbes.dumpCoroutines()

    println("Thread Dump")
    Thread.getAllStackTraces().forEach { (thread, stackTrace) ->
        println("Thread: $thread")
        stackTrace.forEach {
            println("\t$it")
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun platformWaitForAllCoroutines() {
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

@OptIn(ExperimentalCoroutinesApi::class)
private fun List<CoroutineInfo>.dump(): String = buildString {
    this@dump.forEach { info ->
        appendLine("Coroutine: $info")
        info.lastObservedStackTrace().forEach {
            appendLine("\t$it")
        }
    }
}
