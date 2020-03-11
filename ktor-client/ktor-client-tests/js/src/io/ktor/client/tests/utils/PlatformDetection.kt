/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.util.*

@InternalAPI
actual val isKotlinJsIr: Boolean =
    // Hack to detect JS IR backend. Legacy runtime has `Kotlin.kotlin` defined.
    js("(typeof Kotlin == \"undefined\" || typeof Kotlin.kotlin == \"undefined\")").unsafeCast<Boolean>()
