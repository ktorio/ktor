package io.ktor.pipeline

import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

// todo: it is not clear if these helper functions are needed or they shall be replaced with the corresponding code

inline suspend fun runAsync(executor: Executor, noinline body: suspend () -> Unit) = run(executor.asCoroutineDispatcher(), block = body)

