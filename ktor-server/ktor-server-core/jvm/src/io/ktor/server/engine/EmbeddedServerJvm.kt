/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

public actual class EmbeddedServer<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >
actual constructor(
    private val rootConfig: ServerConfig,
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit
) {

    public actual val monitor: Events = rootConfig.environment.monitor

    public actual val environment: ApplicationEnvironment = rootConfig.environment

    public actual val application: Application
        get() = currentApplication()

    public actual val engineConfig: TConfiguration = engineFactory.configuration(engineConfigBlock)
    private val applicationInstanceLock = ReentrantReadWriteLock()
    private var recreateInstance: Boolean = false
    private var applicationClassLoader: ClassLoader? = null
    private var packageWatchKeys = emptyList<WatchKey>()

    private val configuredWatchPath = environment.config.propertyOrNull("ktor.deployment.watch")?.getList().orEmpty()
    private val watchPatterns: List<String> = configuredWatchPath + rootConfig.watchPaths

    private val configModulesNames: List<String> = run {
        environment.config.propertyOrNull("ktor.application.modules")?.getList() ?: emptyList()
    }

    private val modulesNames: List<String> = configModulesNames

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
     * Reload application: destroy it first and then create again
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EmbeddedServer.reload)
     */
    public fun reload() {
        applicationInstanceLock.write {
            destroyApplication()
            val (application, classLoader) = createApplication()
            applicationInstance = application
            applicationClassLoader = classLoader
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
            destroyApplication()
            val (application, classLoader) = createApplication()
            applicationInstance = application
            applicationClassLoader = classLoader
        }

        return@read applicationInstance ?: error("EmbeddedServer was stopped")
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

            environment.log.debug("Changes to $count files caused application restart.")
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

    private fun createApplication(): Pair<Application, ClassLoader> {
        val classLoader = createClassLoader()
        val currentThread = Thread.currentThread()
        val oldThreadClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoader

        try {
            return instantiateAndConfigureApplication(classLoader) to classLoader
        } finally {
            currentThread.contextClassLoader = oldThreadClassLoader
        }
    }

    private fun createClassLoader(): ClassLoader {
        val baseClassLoader = environment.classLoader

        if (!rootConfig.developmentMode) {
            environment.log.info("Autoreload is disabled because the development mode is off.")
            return baseClassLoader
        }

        val watchPatterns = watchPatterns
        if (watchPatterns.isEmpty()) {
            environment.log.info("No ktor.deployment.watch patterns specified, automatic reload is not active.")
            return baseClassLoader
        }

        val allUrls = baseClassLoader.allURLs()
        val jre = File(System.getProperty("java.home")).parent
        val debugUrls = allUrls.map { it.file }
        environment.log.debug("Java Home: $jre")
        environment.log.debug("Class Loader: $baseClassLoader: ${debugUrls.filter { !it.toString().startsWith(jre) }}")

        // we shouldn't watch URL for ktor-server classes, even if they match patterns,
        // because otherwise it loads two ApplicationEnvironment (and other) types which do not match
        val coreUrls = listOf(
            ApplicationEnvironment::class.java, // ktor-server
            Pipeline::class.java, // ktor-parsing
            HttpStatusCode::class.java, // ktor-http
            kotlin.jvm.functions.Function1::class.java, // kotlin-stdlib
            Logger::class.java, // slf4j
            ByteReadChannel::class.java,
            Input::class.java, // kotlinx-io
            Attributes::class.java
        ).mapNotNullTo(HashSet()) { it.protectionDomain.codeSource.location }

        val watchUrls = allUrls.filter { url ->
            url !in coreUrls &&
                watchPatterns.any { pattern -> checkUrlMatches(url, pattern) } &&
                !(url.path ?: "").startsWith(jre)
        }

        if (watchUrls.isEmpty()) {
            environment.log.info(
                "No ktor.deployment.watch patterns match classpath entries, automatic reload is not active"
            )
            return baseClassLoader
        }

        watchUrls(watchUrls)
        return OverridingClassLoader(watchUrls, baseClassLoader)
    }

    private fun safeRaiseEvent(event: EventDefinition<Application>, application: Application) {
        monitor.raiseCatching(event, application)
    }

    private fun destroyApplication() {
        val currentApplication = applicationInstance
        val currentApplicationClassLoader = applicationClassLoader
        applicationInstance = null
        applicationClassLoader = null

        if (currentApplication != null) {
            safeRaiseEvent(ApplicationStopping, currentApplication)
            try {
                currentApplication.dispose()
                (currentApplicationClassLoader as? OverridingClassLoader)?.close()
            } catch (e: Throwable) {
                environment.log.error("Failed to destroy application instance.", e)
            }

            safeRaiseEvent(ApplicationStopped, currentApplication)
        }
        packageWatchKeys.forEach { it.cancel() }
        packageWatchKeys = mutableListOf()
    }

    private fun watchUrls(urls: List<URL>) {
        val paths = HashSet<Path>()
        for (url in urls) {
            val path = url.path ?: continue
            val decodedPath = URLDecoder.decode(path, "utf-8")
            val folder = runCatching { File(decodedPath).toPath() }.getOrNull() ?: continue

            if (!Files.exists(folder)) {
                continue
            }

            val visitor = object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    paths.add(dir)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val dir = file.parent
                    if (dir != null) {
                        paths.add(dir)
                    }

                    return FileVisitResult.CONTINUE
                }
            }

            if (Files.isDirectory(folder)) {
                Files.walkFileTree(folder, visitor)
            }
        }

        paths.forEach { path ->
            environment.log.debug("Watching $path for changes.")
        }

        val modifiers = get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH()?.let { arrayOf(it) } ?: emptyArray()
        packageWatchKeys = paths.mapNotNull { path ->
            watcher?.let {
                path.register(it, arrayOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), *modifiers)
            }
        }
    }

    public actual fun start(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        addShutdownHook { stop() }

        applicationInstanceLock.write {
            val (application, classLoader) = try {
                createApplication()
            } catch (cause: Throwable) {
                destroyApplication()
                if (watchPatterns.isNotEmpty()) {
                    cleanupWatcher()
                }

                throw cause
            }
            applicationInstance = application
            applicationClassLoader = classLoader
        }

        CoroutineScope(application.coroutineContext).launch {
            engine.resolvedConnectors().forEach {
                val host = escapeHostname(it.host)
                environment.log.info(
                    "Responding at ${it.type.name.lowercase()}://$host:${it.port}"
                )
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

    private fun instantiateAndConfigureApplication(currentClassLoader: ClassLoader): Application {
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
            modulesNames.forEach { name ->
                launchModuleByName(name, currentClassLoader, newInstance)
            }

            rootConfig.modules.forEach { module ->
                val name = module.methodName()

                try {
                    launchModuleByName(name, currentClassLoader, newInstance)
                } catch (_: ReloadingException) {
                    module(newInstance)
                }
            }
        }

        safeRaiseEvent(ApplicationStarted, newInstance)
        return newInstance
    }

    private fun launchModuleByName(name: String, currentClassLoader: ClassLoader, newInstance: Application) {
        avoidingDoubleStartupFor(name) {
            executeModuleFunction(currentClassLoader, name, newInstance)
        }
    }

    private fun avoidingDoubleStartup(block: () -> Unit) {
        try {
            block()
        } finally {
            currentStartupModules.get()?.let {
                if (it.isEmpty()) {
                    currentStartupModules.remove()
                }
            }
        }
    }

    private fun avoidingDoubleStartupFor(fqName: String, block: () -> Unit) {
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
    val urlPath = url.path?.replace(File.separatorChar, '/') ?: return false
    val normalizedPattern = pattern.replace(File.separatorChar, '/')
    return urlPath.contains(normalizedPattern, ignoreCase = true)
}
