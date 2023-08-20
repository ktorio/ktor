// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.slf4j.*
import kotlin.coroutines.*

/**
 * Engine environment configuration builder
 */
@KtorDsl
public actual class ApplicationEnvironmentBuilder {
    /**
     * Root class loader
     */
    public var classLoader: ClassLoader = ApplicationEnvironmentBuilder::class.java.classLoader

    /**
     * Parent coroutine context for an application
     */
    public actual var parentCoroutineContext: CoroutineContext = EmptyCoroutineContext

    /**
     * Application logger
     */
    public actual var log: Logger = LoggerFactory.getLogger("io.ktor.server.Application")

    /**
     * Application config
     */
    public actual var config: ApplicationConfig = MapApplicationConfig()

    /**
     * Build an application engine environment
     */
    public actual fun build(shouldReload: Boolean): ApplicationEnvironment {
        return ApplicationEnvironmentImplJvm(classLoader, parentCoroutineContext, log, config, shouldReload)
    }
}

internal class ApplicationEnvironmentImplJvm(
    override val classLoader: ClassLoader,
    parentCoroutineContext: CoroutineContext,
    override val log: Logger,
    override val config: ApplicationConfig,
    isReloading: Boolean,
) : ApplicationEnvironment {

    override val parentCoroutineContext: CoroutineContext = when {
        isReloading -> parentCoroutineContext + ClassLoaderAwareContinuationInterceptor
        else -> parentCoroutineContext
    }
}

private object ClassLoaderAwareContinuationInterceptor : ContinuationInterceptor {
    override val key: CoroutineContext.Key<*> =
        object : CoroutineContext.Key<ClassLoaderAwareContinuationInterceptor> {}

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        val classLoader = Thread.currentThread().contextClassLoader
        return object : Continuation<T> {
            override val context: CoroutineContext = continuation.context

            override fun resumeWith(result: Result<T>) {
                Thread.currentThread().contextClassLoader = classLoader
                continuation.resumeWith(result)
            }
        }
    }
}
