/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal fun testSockets(
    timeout: Duration = 1.minutes,
    block: suspend CoroutineScope.(SelectorManager) -> Unit
): TestResult = runTestWithRealTime(timeout = timeout) {
    SelectorManager().use { selector ->
        block(selector)
    }
}

internal expect fun Any.supportsUnixDomainSockets(): Boolean

internal expect fun Throwable.isPosixException(): Boolean

@OptIn(ExperimentalUuidApi::class)
internal fun createTempFilePath(basename: String): String {
    return Path(SystemTemporaryDirectory, "$basename-${Uuid.random()}").toString()
}

internal fun removeFile(path: String) {
    SystemFileSystem.delete(Path(path), mustExist = false)
}
