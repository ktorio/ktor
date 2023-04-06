/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import kotlin.coroutines.intrinsics.*

internal actual fun <TSubject : Any, TContext : Any> PipelineInterceptor<TSubject, TContext>.toPipelineInterceptor():
    PipelineInterceptorFunction<TSubject, TContext> = { scope, subject, c ->
    val wrappedBlock: suspend () -> Unit = {
        scope.this@toPipelineInterceptor(subject)
    }
    wrappedBlock.startCoroutineUninterceptedOrReturn(c)
}
