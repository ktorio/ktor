/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.io.files.*
import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.*

internal fun testSockets(
    timeout: Duration = 1.minutes,
    block: suspend CoroutineScope.(SelectorManager) -> Unit
): TestResult = runTestWithRealTime(timeout = timeout) {
    SelectorManager().use { selector ->
        block(selector)
    }
}

internal expect fun Any.supportsUnixDomainSockets(): Boolean

@OptIn(ExperimentalUuidApi::class)
internal fun createTempFilePath(basename: String): String {
    return Path(SystemTemporaryDirectory, "$basename-${Uuid.random()}").toString()
}

internal fun removeFile(path: String) {
    SystemFileSystem.delete(Path(path), mustExist = false)
}
