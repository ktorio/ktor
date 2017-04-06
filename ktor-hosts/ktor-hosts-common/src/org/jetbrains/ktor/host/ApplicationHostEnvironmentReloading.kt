package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import java.io.*
import java.lang.reflect.*
import java.net.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Implements [ApplicationHostEnvironment] by loading an [Application] from a folder or jar.
 *
 * When [automaticReload] is `true`, it watches changes in folder/jar and implements hot reloading
 */
class ApplicationHostEnvironmentReloading(
        override val classLoader: ClassLoader,
        override val log: ApplicationLog,
        override val config: ApplicationConfig,
        override val connectors: List<HostConnectorConfig>,
        override val executor: ScheduledExecutorService,
        val directModules: List<Application.() -> Unit>,
        val automaticReload: Boolean = false)
    : ApplicationHostEnvironment {
    private var _applicationInstance: Application? = null
    private val applicationInstanceLock = ReentrantReadWriteLock()
    private var packageWatchKeys = emptyList<WatchKey>()

    private val configModules: List<String>? = config.propertyOrNull("ktor.application.modules")?.getList()

    private val watchPatterns: List<String> = config.propertyOrNull("ktor.deployment.watch")?.getList() ?: listOf()
    private val watcher by lazy { FileSystems.getDefault().newWatchService() }

    override val monitor = ApplicationMonitor().logEvents()

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    override val application: Application
        get() = applicationInstanceLock.read {
            if (automaticReload) {
                val changes = packageWatchKeys.flatMap { it.pollEvents() }
                if (changes.isNotEmpty()) {
                    log.info("Changes in application detected.")
                    var count = changes.size
                    while (true) {
                        Thread.sleep(200)
                        val moreChanges = packageWatchKeys.flatMap { it.pollEvents() }
                        if (moreChanges.isEmpty())
                            break
                        log.debug("Waiting for more changes.")
                        count += moreChanges.size
                    }

                    log.debug("Changes to $count files caused application restart.")
                    changes.take(5).forEach { log.debug("...  ${it.context()}") }
                    destroyApplication()
                }
            }

            _applicationInstance ?: applicationInstanceLock.write {
                val newApplication = createApplication()
                _applicationInstance = newApplication
                newApplication
            }
        }

    fun ClassLoader.allURLs(): List<URL> {
        val parentUrls = parent?.allURLs() ?: emptyList()
        if (this is URLClassLoader) {
            val urls = urLs.filterNotNull()
            log.debug("ClassLoader $this: $urls")
            return urls + parentUrls
        }
        return parentUrls
    }

    private fun createApplication(): Application {
        val classLoader = createClassLoader()
        val currentThread = Thread.currentThread()
        val oldThreadClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoader
        try {
            return instantiateAndConfigureApplication(classLoader).also {
                monitor.applicationStart(it)
            }
        } finally {
            currentThread.contextClassLoader = oldThreadClassLoader
        }
    }

    private fun createClassLoader(): ClassLoader {
        val baseClassLoader = classLoader
        if (!automaticReload)
            return baseClassLoader

        val allUrls = baseClassLoader.allURLs()
        val watchPatterns = watchPatterns
        if (watchPatterns.isEmpty()) {
            log.warning("No ktor.deployment.watch patterns specified, hot reload is disabled")
            return baseClassLoader
        }

        // we shouldn't watch URL for ktor-core classes, even if they match patterns,
        // because otherwise it loads two ApplicationEnvironment (and other) types which do not match
        val coreUrl = ApplicationEnvironment::class.java.protectionDomain.codeSource.location

        val watchUrls = allUrls.filter { url ->
            url != coreUrl && watchPatterns.any { pattern -> url.toString().contains(pattern) }
        }

        if (!watchUrls.isNotEmpty()) {
            log.warning("No ktor.deployment.watch patterns match classpath entries, hot reload is disabled")
            return baseClassLoader
        }

        watchUrls(watchUrls)
        return OverridingClassLoader(watchUrls, baseClassLoader)
    }

    fun destroyApplication() {
        applicationInstanceLock.write {
            val currentApplication = _applicationInstance
            if (currentApplication != null) {
                try {
                    monitor.applicationStop(currentApplication)
                    currentApplication.dispose()
                } catch(e: Throwable) {
                    log.error("Failed to destroy application instance.", e)
                }
            }
            _applicationInstance = null
            packageWatchKeys.forEach { it.cancel() }
            packageWatchKeys = mutableListOf()
        }
    }

    fun watchUrls(urls: List<URL>) {
        val paths = HashSet<Path>()
        for (url in urls) {
            val path = url.path
            if (path != null) {
                val folder = File(URLDecoder.decode(path, "utf-8")).toPath()
                val visitor = object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        paths.add(dir)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val dir = file.parent
                        if (dir != null)
                            paths.add(dir)
                        return FileVisitResult.CONTINUE
                    }
                }
                Files.walkFileTree(folder, visitor)
            }
        }

        paths.forEach { path ->
            log.debug("Watching $path for changes.")
        }
        val modifiers = get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH()?.let { arrayOf(it) } ?: emptyArray()
        packageWatchKeys = paths.map {
            it.register(watcher, arrayOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), *modifiers)
        }
    }

    override fun start() {
        application // create an application and notify monitor
    }

    override fun stop() {
        destroyApplication()
        if (automaticReload) {
            watcher.close()
        }
    }

    private fun instantiateAndConfigureApplication(classLoader: ClassLoader): Application {
        val application = Application(this)

        configModules?.forEach { fqName ->
            executeModuleFunction(classLoader, fqName, application)
        }
        directModules.forEach { it(application) }
        return application
    }

    private fun executeModuleFunction(classLoader: ClassLoader, fqName: String, application: Application) {
        fqName.lastIndexOfAny(".#".toCharArray()).let { idx ->
            if (idx == -1) return@let
            val className = fqName.substring(0, idx)
            val functionName = fqName.substring(idx + 1)
            val clazz = classLoader.loadClassOrNull(className) ?: return@let
            val kclass = clazz.kotlin

            clazz.methods
                    .filter { it.name == functionName && Modifier.isStatic(it.modifiers) }
                    .mapNotNull { it.kotlinFunction }
                    .bestFunction()?.let { moduleFunction ->

                callEntryPointFunction(null, moduleFunction, application)
                return
            }

            val instance = createModuleContainer(clazz, application)
            kclass.functions.filter { it.name == functionName }.bestFunction()?.let { moduleFunction ->
                callEntryPointFunction(instance, moduleFunction, application)
                return
            }
        }

        throw ClassNotFoundException("Module function cannot be found for the fully qualified name '$fqName'")
    }

    private fun createModuleContainer(applicationEntryClass: Class<*>, application: Application?): Any {
        return applicationEntryClass.kotlin.objectInstance ?: run {
            val constructors = applicationEntryClass.kotlin.constructors.filter {
                it.parameters.all { p -> p.isOptional || isApplicationEnvironment(p) || (isApplication(p) && application != null) }
            }

            val constructor = constructors.bestFunction() ?: throw RuntimeException("There are no applicable constructors found in class $applicationEntryClass")
            callEntryPointFunction(null, constructor, application)
        }
    }

    private fun <R> List<KFunction<R>>.bestFunction() = sortedWith(compareBy({ it.parameters.count { !it.isOptional } }, { it.parameters.size })).lastOrNull()

    private fun <R> callEntryPointFunction(instance: Any?, entryPoint: KFunction<R>, application: Application?): R {
        return entryPoint.callBy(entryPoint.parameters
                .filterNot { it.isOptional }
                .associateBy({ it }, { p ->
                    @Suppress("IMPLICIT_CAST_TO_ANY")
                    when {
                        p.kind == KParameter.Kind.INSTANCE -> instance
                        isApplicationEnvironment(p) -> this
                        isApplication(p) -> application ?: throw IllegalArgumentException("Couldn't inject application instance to $entryPoint")
                        else -> throw RuntimeException("Parameter type ${p.type} of parameter ${p.name} is not supported")
                    }
                }))
    }

    private fun ClassLoader.loadClassOrNull(name: String): Class<*>? {
        try {
            return loadClass(name)
        } catch (e: ClassNotFoundException) {
            return null
        }
    }

    private fun get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH(): WatchEvent.Modifier? {
        try {
            val c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier")
            val f = c.getField("HIGH")
            return f.get(c) as? WatchEvent.Modifier
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        private fun isParameterOfType(p: KParameter, type: Class<*>) = (p.type.javaType as? Class<*>)?.let { type.isAssignableFrom(it) } ?: false
        private fun isApplicationEnvironment(p: KParameter) = isParameterOfType(p, ApplicationEnvironmentClassInstance)
        private fun isApplication(p: KParameter) = isParameterOfType(p, ApplicationClassInstance)

        private val ApplicationEnvironmentClassInstance = ApplicationEnvironment::class.java
        private val ApplicationClassInstance = Application::class.java
    }
}

