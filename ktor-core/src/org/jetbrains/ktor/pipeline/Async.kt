package org.jetbrains.ktor.pipeline

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import java.util.concurrent.*

suspend fun runAsync(executor: Executor, body: suspend () -> Unit) = future(executor.asCoroutineDispatcher(), body).await()