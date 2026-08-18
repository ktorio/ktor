/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.events.EventDefinition
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.internal.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.net.URLDecoder
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.getOrSet
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.Duration.Companion.milliseconds

private typealias ApplicationModule = suspend Application.() -> Unit

public actual class EmbeddedServer<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >
actual constructor(
    private val rootConfig: ServerConfig,
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit
) {
    init {
        embeddedServerInstances.add(this)
    }

    @Suppress("DEPRECATION")
    public actual val monitor: Events = rootConfig.environment.monitor

    public actual val environment: ApplicationEnvironment = rootConfig.environment

    public actual val application: Application
        get() = currentApplication()

    public actual val engineConfig: TConfiguration = engineFactory.configuration(engineConfigBlock)
    private val applicationInstanceLock = ReentrantReadWriteLock()
    private var recreateInstance: Boolean = false
    private var packageWatchKeys = emptyList<WatchKey>()

    private val configuredWatchPath = environment.config.propertyOrNull("ktor.deployment.watch")?.getList().orEmpty()
    private val watchPatterns: List<String> = configuredWatchPath + rootConfig.watchPaths

    @OptIn(InternalAPI::class)
    private val moduleInjector: ModuleParametersInjector by lazy {
        loadServiceOrNull() ?: ModuleParametersInjector.Disabled
    }
    private val modules: List<DynamicApplicationModule> get() =
        environment.moduleConfigReferences.map(::dynamicModule) +
            rootConfig.modules.map { module -> module.toDynamicModuleOrNull() ?: module.wrapWithDynamicModule() }

    private var applicationInstance: Application? = Application(
        environment,
        rootConfig.developmentMode,
        rootConfig.rootPath,
        monitor,
        rootConfig.parentCoroutineContext,
        ::engine
    )

    public actual val engine: TEngine = engineFactory.create(
        environment,
        monitor,
        rootConfig.developmentMode,
        engineConfig,
        ::currentApplication
    )

    private val watcher: WatchService? by lazy {
        try {
            FileSystems.getDefault().newWatchService()
        } catch (_: NoClassDefFoundError) {
            null
        }
    }

    /**
     * Reload application: build a new instance first, then dispose of the previous one.
     *
     * If the new application cannot be created (for example, because the user code throws during
     * module loading), the previous instance is preserved and the failure is rethrown.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EmbeddedServer.reload)
     */
    public fun reload() {
        applicationInstanceLock.write {
            reloadApplication()
        }
    }

    private fun currentApplication(): Application = applicationInstanceLock.read {
        val currentApplication = applicationInstance ?: error("EmbeddedServer was stopped")

        if (!rootConfig.developmentMode) {
            return@read currentApplication
        }

        if (getFileChanges().isNullOrEmpty()) {
            return@read currentApplication
        }

        applicationInstanceLock.write {
            try {
                reloadApplication()
            } catch (cause: Throwable) {
                environment.log.error(
                    "Auto-reload failed; continuing to serve the previously loaded application.",
                    cause,
                )
            }
        }

        return@read applicationInstance ?: error("EmbeddedServer was stopped")
    }

    /**
     * Build a new application and swap it in, disposing of the previous one only on success.
     *
     * On failure the previous application, its class loader, and the registered watch keys
     * are left intact so the server keeps serving requests against the last known-good code.
     *
     * Must be called while holding the write lock on [applicationInstanceLock].
     */
    private fun reloadApplication() {
        val previousApplication = applicationInstance
        val previousWatchKeys = packageWatchKeys

        val newApplication = try {
            instantiateAndConfigureApplication()
        } catch (cause: Throwable) {
            // createClassLoader -> watchUrls() may have already replaced packageWatchKeys before
            // instantiateAndConfigureApplication() failed. Cancel those freshly-registered keys
            // (they belong to a class loader we are discarding) and restore the previous ones.
            if (packageWatchKeys !== previousWatchKeys) {
                packageWatchKeys.forEach { it.cancel() }
                packageWatchKeys = previousWatchKeys
            }
            throw cause
        }

        if (previousApplication != null) {
            safeRaiseEvent(ApplicationStopping, previousApplication)
            try {
                destroyBlocking(previousApplication)
            } catch (e: Throwable) {
                environment.log.error("Failed to destroy previous application instance.", e)
            }
            safeRaiseEvent(ApplicationStopped, previousApplication)
        }
        if (packageWatchKeys !== previousWatchKeys) {
            previousWatchKeys.forEach { it.cancel() }
        }

        applicationInstance = newApplication
    }

    private fun getFileChanges(): List<WatchEvent<*>>? {
        try {
            val changes = packageWatchKeys.flatMap { it.pollEvents() }
            if (changes.isEmpty()) {
                return changes
            }

            environment.log.info("Changes in application detected.")

            var count = changes.size
            while (true) {
                Thread.sleep(200)
                val moreChanges = packageWatchKeys.flatMap { it.pollEvents() }
                if (moreChanges.isEmpty()) {
                    break
                }

                environment.log.debug("Waiting for more changes.")
                count += moreChanges.size
            }

            environment.log.debug { "Changes to $count files caused application restart." }
            changes.take(5).forEach { environment.log.debug("...  {}", it.context()) }
            return changes
        } catch (e: InterruptedException) {
            environment.log.debug("Watch service was interrupted", e)
            return null
        } catch (e: ClosedWatchServiceException) {
            environment.log.debug("Watch service was closed", e)
            return null
        }
    }

    private fun safeRaiseEvent(event: EventDefinition<Application>, application: Application) {
        try {
            monitor.raise(event, application)
        } catch (cause: Throwable) {
            environment.log.debug("One or more of the handlers thrown an exception", cause)
        }
    }

    private fun destroyApplication() {
        val currentApplication = applicationInstance
        applicationInstance = null

        if (currentApplication != null) {
            safeRaiseEvent(ApplicationStopping, currentApplication)
            try {
                destroyBlocking(currentApplication)
            } catch (e: Throwable) {
                environment.log.error("Failed to destroy application instance.", e)
            }
            safeRaiseEvent(ApplicationStopped, currentApplication)
        }
        packageWatchKeys.forEach { it.cancel() }
        packageWatchKeys = mutableListOf()
    }

    @OptIn(InternalAPI::class)
    private fun destroyBlocking(application: Application) {
        runBlocking {
            withTimeout(engineConfig.shutdownTimeout.milliseconds) {
                application.disposeAndJoin()
            }
        }
    }

    public actual fun start(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        addShutdownHook { stop() }

        applicationInstanceLock.write {
            val application = try {
                instantiateAndConfigureApplication()
            } catch (cause: Throwable) {
                destroyApplication()
                if (watchPatterns.isNotEmpty()) {
                    cleanupWatcher()
                }

                throw cause
            }
            applicationInstance = application
        }

        CoroutineScope(application.coroutineContext).launch {
            engine.resolvedConnectors().forEach {
                val address = it.addressDescription
                    ?: "${it.type.name.lowercase()}://${escapeHostname(it.host)}:${it.port}"
                environment.log.info("Responding at $address")
            }
        }

        engine.start(wait)
        return this
    }

    public actual suspend fun startSuspend(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        return withContext(Dispatchers.IOBridge) { start(wait) }
    }

    public fun stop(shutdownGracePeriod: Long, shutdownTimeout: Long, timeUnit: TimeUnit) {
        try {
            engine.stop(timeUnit.toMillis(shutdownGracePeriod), timeUnit.toMillis(shutdownTimeout))
        } catch (e: Exception) {
            environment.log.warn("Exception occurred during engine shutdown", e)
        }
        applicationInstanceLock.write {
            destroyApplication()
        }
        if (watchPatterns.isNotEmpty()) {
            cleanupWatcher()
        }
    }

    public actual fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        stop(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
    }

    public actual suspend fun stopSuspend(gracePeriodMillis: Long, timeoutMillis: Long) {
        withContext(Dispatchers.IOBridge) { stop(gracePeriodMillis, timeoutMillis) }
    }

    private fun instantiateAndConfigureApplication(): Application {
        val newInstance = if (recreateInstance || applicationInstance == null) {
            Application(
                environment,
                rootConfig.developmentMode,
                rootConfig.rootPath,
                monitor,
                rootConfig.parentCoroutineContext,
                ::engine
            )
        } else {
            recreateInstance = true
            applicationInstance!!
        }

        safeRaiseEvent(ApplicationStarting, newInstance)

        avoidingDoubleStartup {
            withTimeout(environment.startupTimeout) {
                environment.moduleLoader.loadModules(
                    newInstance,
                    Thread.currentThread().contextClassLoader,
                    modules,
                )
            }
        }

        monitor.raise(ApplicationModulesLoaded, newInstance)
        monitor.raise(ApplicationStarted, newInstance)

        return newInstance
    }

    private fun dynamicModule(name: String): DynamicApplicationModule {
        return DynamicApplicationModule(name) { classLoader ->
            val application = this
            launchModuleByName(name, classLoader, application)
        }
    }

    private fun ApplicationModule.toDynamicModuleOrNull(): DynamicApplicationModule? {
        // Programmatic modules are loaded dynamically only when development mode is active
        if (!rootConfig.developmentMode) return null

        val module = this
        val name = methodNameOrNull() ?: return null

        return DynamicApplicationModule(name) { classLoader ->
            val application = this
            try {
                launchModuleByName(name, classLoader, application)
            } catch (cause: ReloadingException) {
                environment.log.debug(
                    "Failed to load module '$name' by classpath reference, falling back to currently loaded value",
                    cause,
                )
                module.invoke(application)
            }
        }
    }

    /**
     * Method name getting might fail if method signature has been changed after compilation
     * (for example by R8 or ProGuard).
     *
     * We must also filter out function names with $, assuming they are anonymous.
     */
    private fun ApplicationModule.methodNameOrNull(): String? =
        runCatching {
            this@methodNameOrNull.methodName()
        }.onFailure { cause ->
            environment.log.debug("Module can't be loaded dynamically; auto-reloading unavailable", cause)
        }.getOrNull()?.takeIf {
            '$' !in it
        }

    private fun ApplicationModule.wrapWithDynamicModule(): DynamicApplicationModule {
        val module = this
        return DynamicApplicationModule { module() }
    }

    private suspend fun launchModuleByName(name: String, currentClassLoader: ClassLoader, newInstance: Application) {
        avoidingDoubleStartupFor(name) {
            executeModuleFunction(currentClassLoader, name, newInstance, moduleInjector)
        }
    }

    private fun avoidingDoubleStartup(block: suspend () -> Unit) {
        try {
            runBlocking {
                block()
            }
        } finally {
            currentStartupModules.get()?.let {
                if (it.isEmpty()) {
                    currentStartupModules.remove()
                }
            }
        }
    }

    private suspend fun avoidingDoubleStartupFor(fqName: String, block: suspend () -> Unit) {
        val modules = currentStartupModules.getOrSet { ArrayList(1) }
        check(!modules.contains(fqName)) {
            "Module startup is already in progress for function $fqName (recursive module startup from module main?)"
        }

        modules.add(fqName)
        try {
            block()
        } finally {
            modules.remove(fqName)
        }
    }

    private fun cleanupWatcher() {
        runCatching { watcher?.close() }
    }
}

internal fun checkUrlMatches(url: URL, pattern: String): Boolean {
    val rawPath = url.path ?: return false
    val urlPath = URLDecoder.decode(rawPath, "utf-8").replace(File.separatorChar, '/')
    val normalizedPattern = pattern.replace(File.separatorChar, '/')
    return urlPath.contains(normalizedPattern, ignoreCase = true)
}
