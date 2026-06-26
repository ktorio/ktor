/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/**
 * Same as [kotlin.test.Ignore] but only for wasm-wasi platform.
 * It's mostly needed because there is no engine yet, and there are no sockets yet.
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class WasmWasiIgnore()
