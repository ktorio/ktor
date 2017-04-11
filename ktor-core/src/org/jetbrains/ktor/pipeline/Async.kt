package org.jetbrains.ktor.pipeline

import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.run
import java.util.concurrent.Executor

// todo: it is not clear if these helper functions are needed or they shall be replaced with the corresponding code

suspend fun runAsync(executor: Executor, body: suspend () -> Unit) = run(executor.asCoroutineDispatcher(), body)

fun launchAsync(executor: Executor, body: suspend () -> Unit) = launch(executor.asCoroutineDispatcher()) { body() }
