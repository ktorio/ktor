package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import java.io.*
import java.net.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.*
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * Implements [ApplicationLifecycle] by loading an [Application] from a folder or jar.
 *
 * When [autoreload] is `true`, it watches changes in folder/jar and implements hot reloading
 */
class ApplicationLoader(val environment: ApplicationEnvironment, val autoreload: Boolean) : ApplicationLifecycle {
    private var _applicationInstance: Application? = null
    private val applicationInstanceLock = Object()
    private val packageWatchKeys = ArrayList<WatchKey>()
    private val log = environment.log.fork("Loader")
    private val applicationClassName: String = environment.config.property("ktor.application.class").getString()
    private val watchPatterns: List<String> = environment.config.propertyOrNull("ktor.deployment.watch")?.getList() ?: listOf()
    private val watcher by lazy { FileSystems.getDefault().newWatchService() }
    private val appInitInterceptors = ArrayList<Application.() -> Unit>()

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    override val application: Application
        get() = synchronized(applicationInstanceLock) {
            if (autoreload) {
                val changes = packageWatchKeys.flatMap { it.pollEvents() }
                if (changes.size > 0) {
                    log.info("Changes in application detected.")
                    var count = changes.size
                    while (true) {
                        Thread.sleep(200)
                        val moreChanges = packageWatchKeys.flatMap { it.pollEvents() }
                        if (moreChanges.size == 0)
                            break
                        log.debug("Waiting for more changes.")
                        count += moreChanges.size
                    }

                    log.debug("Changes to $count files caused application restart.")
                    changes.take(5).forEach { log.debug("...  ${it.context()}") }
                    destroyApplication()
                    _applicationInstance = null
                }
            }

            var instance = _applicationInstance
            if (instance == null) {
                instance = createApplication()
                _applicationInstance = instance
            }
            instance!!
        }

    override fun onBeforeInitializeApplication(initializer: Application.() -> Unit) {
        appInitInterceptors.add(initializer)
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
        val classLoader = if (autoreload) {
            val allUrls = environment.classLoader.allURLs()
            val watchPatterns = watchPatterns

            // we shouldn't watch URL for ktor-core classes, even if they match patterns,
            // because otherwise it loads two ApplicationEnvironment (and other) types which do not match
            val coreUrl = ApplicationEnvironment::class.java.protectionDomain.codeSource.location

            val watchUrls = allUrls.filter { url ->
                url != coreUrl && watchPatterns.any { pattern -> url.toString().contains(pattern) }
            }

            if (watchUrls.isNotEmpty()) {
                watchUrls(watchUrls)
                OverridingClassLoader(watchUrls, environment.classLoader)
            } else {
                log.warning("No ktor.deployment.watch patterns specified: hot reload is disabled")
                environment.classLoader
            }
        } else
            environment.classLoader

        val currentThread = Thread.currentThread()
        val oldThreadClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoader
        try {
            val applicationClass = classLoader.loadClass(applicationClassName)
                    ?: throw RuntimeException("Application class $applicationClassName cannot be loaded")
            log.debug("Application class: $applicationClass in ${applicationClass.classLoader}")

            val appEnvClass = ApplicationEnvironment::class.java
            fun isApplicationEnvironment(p: KParameter) = (p.type.javaType as? Class<*>)?.let { appEnvClass.isAssignableFrom(it) } ?: false

            val applicationEntryPoint = if (applicationClass.kotlin.objectInstance == null) {
                val constructors = applicationClass.kotlin.constructors.filter { it.parameters.all { p -> p.isOptional || isApplicationEnvironment(p) } }
                if (constructors.isEmpty()) {
                    throw RuntimeException("There are no applicable constructors found in class $applicationClass")
                }

                val constructor = constructors.maxBy { it.parameters.size }!!
                constructor.callBy(constructor.parameters
                        .filterNot { it.isOptional }
                        .associateBy({ it }, { p ->
                            when {
                                isApplicationEnvironment(p) -> environment
                                else -> throw RuntimeException("Parameter type ${p.type} of parameter ${p.name} is not supported")
                            }
                        })
                )
            } else {
                applicationClass.kotlin.objectInstance
            }

            val application = when (applicationEntryPoint) {
                is Application -> applicationEntryPoint
                is ApplicationFeature<*, *, *> -> Application(environment, Unit)
                else -> throw RuntimeException("Application class $applicationClassName should inherit from ${Application::class} or ${ApplicationFeature::class}<${Application::class.simpleName}, *>")
            }

            appInitInterceptors.forEach {
                it(application)
            }

            if (applicationEntryPoint is ApplicationFeature<*, *, *>) {
                @Suppress("UNCHECKED_CAST")
                (applicationEntryPoint as ApplicationFeature<Application, *, *>).install(application, {})
            }

            return application
        } finally {
            currentThread.contextClassLoader = oldThreadClassLoader
        }
    }

    fun destroyApplication() {
        synchronized(applicationInstanceLock) {
            try {
                _applicationInstance?.dispose()
            } catch(e: Throwable) {
                log.error("Failed to destroy application instance.", e)
            }
            packageWatchKeys.forEach { it.cancel() }
            packageWatchKeys.clear()
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
        packageWatchKeys.addAll(paths.map {
            it.register(watcher, arrayOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), *modifiers)
        })
    }

    override fun dispose() {
        destroyApplication()
        if (autoreload) {
            watcher.close()
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
}

