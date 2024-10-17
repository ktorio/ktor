/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.*
import java.util.*

internal actual val enginesToTest: Iterable<HttpClientEngineFactory<HttpClientEngineConfig>> by lazy {
    ServiceLoader.load(
        HttpClientEngineContainer::class.java,
        HttpClientEngineContainer::class.java.classLoader
    ).map { it.factory }
}
internal actual val platformName: String by lazy {
    val os = System.getProperty("os.name", "unknown").lowercase(Locale.getDefault())
    "jvm/" + when {
        os.contains("win") -> "win"
        os.contains("mac") -> "mac"
        os.contains("nux") -> "unix"
        else               -> "unknown"
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
