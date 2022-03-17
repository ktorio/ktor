/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import kotlinx.coroutines.*
import kotlin.time.*

// TODO: can be extracted from `server-testing` module, fully commonized(and js and mingw targets) and used in all ktor-tests by default
// it supports:
//   * timeout - for JVM via junit rule, on nix(and possibly all other targets) via `withTimeout`
//   * test name - for JVM via junit rule, on nix via block qualified name (for that, should be called like `fun test() = runTest {}
//   * collecting and reporting unhandled errors - for JVM via junit rule, on nix via synchronized list and assertion in `AfterTest`
expect abstract class BaseTest() {
    open val timeout: Duration
    fun collectUnhandledException(error: Throwable) // TODO: better name?
    fun runTest(block: suspend CoroutineScope.() -> Unit)
}
