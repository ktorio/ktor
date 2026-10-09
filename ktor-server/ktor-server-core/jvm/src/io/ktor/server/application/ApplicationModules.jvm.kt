/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.*

internal class DynamicApplicationModule(
    val reference: String? = null,
    val function: suspend Application.(ClassLoader) -> Unit,
) {
    suspend inline operator fun invoke(application: Application, classLoader: ClassLoader) =
        application.function(classLoader)
}

internal val ApplicationEnvironment.moduleLoader: ApplicationModuleLoader get() =
    when (startupMode) {
        ApplicationStartupMode.SEQUENTIAL -> LoadSequentially
        ApplicationStartupMode.CONCURRENT -> LoadConcurrently
    }

internal fun interface ApplicationModuleLoader {
    suspend fun loadModules(
        application: Application,
        classLoader: ClassLoader,
        modules: List<DynamicApplicationModule>
    )
}

internal val LoadSequentially = ApplicationModuleLoader { application, classLoader, modules ->
    // triggered immediately since all module functions are blocking
    application.monitor.raise(ApplicationModulesLoading, application)

    // load each module in sequence
    for (module in modules) {
        module(application, classLoader)
    }
}

@OptIn(InternalAPI::class)
internal val LoadConcurrently = ApplicationModuleLoader { application, classLoader, modules ->
    val errors = mutableListOf<Throwable>()

    val parallelModuleTracker = ParallelModuleTracker()

    withContext(
        application.coroutineContext +
            CoroutineExceptionHandler { _, e ->
                application.environment.log.error("Failed to load module", e)
                errors.add(e)
            } +
            Dispatchers.Default.limitedParallelism(1) +
            parallelModuleTracker
    ) {
        val jobs = modules.map { module ->
            parallelModuleTracker.increment()
            launch {
                try {
                    module(application, classLoader)
                } catch (cause: CancellationException) {
                    parallelModuleTracker.abort(cause)
                    throw cause
                } finally {
                    parallelModuleTracker.decrement()
                }
            }
        }

        // wait until every module has either finished or is suspended on a missing dependency
        parallelModuleTracker.awaitIdle()

        application.monitor.raise(ApplicationModulesLoading, application)

        // wait for all modules to finish
        jobs.joinAll()

        if (errors.isNotEmpty()) {
            throw errors.first().apply {
                errors.drop(1).forEach { addSuppressed(it) }
            }
        }
    }
}
