/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.cio

import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("KDocMissingDocumentation", "unused")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
object NoopContinuation : Continuation<Any?> {
    override fun resumeWith(result: Result<Any?>) {}

    override val context: CoroutineContext = Dispatchers.Unconfined
}
