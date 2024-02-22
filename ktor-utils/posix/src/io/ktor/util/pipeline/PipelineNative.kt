/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

@Suppress("UNCHECKED_CAST")
internal actual fun <TSubject : Any, TContext : Any>
    PipelineInterceptor<TSubject, TContext>.toFunction(): PipelineInterceptorFunction<TSubject, TContext> =
    this as PipelineInterceptorFunction<TSubject, TContext>

@Suppress("UNCHECKED_CAST")
internal actual fun <TSubject : Any, TContext : Any>
    PipelineInterceptorFunction<TSubject, TContext>.toInterceptor(): PipelineInterceptor<TSubject, TContext> =
    this as PipelineInterceptor<TSubject, TContext>
