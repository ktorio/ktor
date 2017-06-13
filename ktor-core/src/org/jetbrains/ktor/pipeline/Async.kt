package org.jetbrains.ktor.pipeline

import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

// todo: it is not clear if these helper functions are needed or they shall be replaced with the corresponding code

suspend fun runAsync(executor: Executor, body: suspend () -> Unit) = run(executor.asCoroutineDispatcher(), block = body)

fun launchAsync(executor: Executor, body: suspend () -> Unit) = launch(executor.asCoroutineDispatcher()) { body() }
