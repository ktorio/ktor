package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import java.io.*
import java.lang.reflect.*
import java.net.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.*
import java.util.*
import java.util.concurrent.locks.*
import kotlin.comparisons.*
import kotlin.concurrent.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * Implements [ApplicationLifecycle] by loading an [Application] from a folder or jar.
 *
 * When [autoreload] is `true`, it watches changes in folder/jar and implements hot reloading
 */
class ApplicationLoader(val environment: ApplicationEnvironment, val autoreload: Boolean) : ApplicationLifecycle {
    private var _applicationInstance: Application? = null
    private val applicationInstanceLock = ReentrantReadWriteLock()
    private var packageWatchKeys = emptyList<WatchKey>()
    private val log = environment.log.fork("Loader")

    private val applicationClassName: String? = environment.config.propertyOrNull("ktor.application.class")?.getString()
    private val applicationFeatures: List<String>? = environment.config.propertyOrNull("ktor.application.features")?.getList()
    private val applicationModules: List<String>? = environment.config.propertyOrNull("ktor.application.modules")?.getList()

    private val watchPatterns: List<String> = environment.config.propertyOrNull("ktor.deployment.watch")?.getList() ?: listOf()
    private val watcher by lazy { FileSystems.getDefault().newWatchService() }
    private val appInitInterceptors = ArrayList<Application.() -> Unit>()

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    override val application: Application
        get() = applicationInstanceLock.read {
            if (autoreload) {
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
            return instantiateAndConfigureApplication(classLoader)
        } finally {
            currentThread.contextClassLoader = oldThreadClassLoader
        }
    }

    fun destroyApplication() {
        applicationInstanceLock.write {
            try {
                _applicationInstance?.dispose()
            } catch(e: Throwable) {
                log.error("Failed to destroy application instance.", e)
            } finally {
                _applicationInstance = null
            }

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

    override fun dispose() {
        destroyApplication()
        if (autoreload) {
            watcher.close()
        }
    }

    private fun instantiateAndConfigureApplication(classLoader: ClassLoader): Application {
        val application = applicationClassName?.let {
            val applicationClass = classLoader.loadClass(it)
            if (ApplicationFeatureClassInstance.isAssignableFrom(applicationClass)) {
                throw IllegalArgumentException("$applicationClassName: use ktor.application.modules and ktor.application.features instead")
            }
            if (!ApplicationClassInstance.isAssignableFrom(applicationClass)) {
                throw IllegalArgumentException("$applicationClassName (ktor.application.class) should inherit ${ApplicationClassInstance.name}")
            }

            createApplicationEntry(applicationClass, null) as Application
        } ?: Application(environment, Unit)

        appInitInterceptors.forEach {
            it(application)
        }

        applicationFeatures?.forEach { featureFqName ->
            instantiateAndConfigure(classLoader, featureFqName, application)
        }

        applicationModules?.forEach { fqName ->
            instantiateAndConfigure(classLoader, fqName, application)
        }

        return application
    }

    private fun instantiateAndConfigure(classLoader: ClassLoader, fqName: String, application: Application) {
        classLoader.loadClassOrNull(fqName)?.let { clazz ->
            val entry = createApplicationEntry(clazz, application)

            when (entry) {
                is ApplicationModule -> {
                    @Suppress("UNCHECKED_CAST")
                    application.install(entry)
                }
                is ApplicationFeature<*, *, *> -> {
                    // TODO validate install function signature to ensure first parameter is Application
                    @Suppress("UNCHECKED_CAST")
                    application.install(entry as ApplicationFeature<Application, *, *>)
                }
                else -> throw IllegalArgumentException("Entry point $fqName should be ApplicationModule or ApplicationFeature<Application, *, *>")
            }

            return
        }

        fqName.lastIndexOfAny(".#".toCharArray()).let { idx ->
            if (idx == -1) return@let
            val className = fqName.substring(0, idx)
            val functionName = fqName.substring(idx + 1)
            val clazz = classLoader.loadClassOrNull(className)

            if (clazz != null) {
                val kclass = clazz.kotlin

                clazz.methods.filter { it.name == functionName && Modifier.isStatic(it.modifiers) }
                        .mapNotNull { it.kotlinFunction }
                        .nullIfEmpty()
                        ?.bestFunction()?.let { moduleFunction ->

                    callEntryPointFunction(null, moduleFunction, application)
                    return
                }

                val instance = createApplicationEntry(clazz, application)
                kclass.functions.filter { it.name == functionName }.nullIfEmpty()?.bestFunction()?.let { moduleFunction ->
                    callEntryPointFunction(instance, moduleFunction, application)
                    return
                }
            }
        }

        throw ClassNotFoundException("Neither function nor class exist for the fully qualified name $fqName")
    }

    private fun createApplicationEntry(applicationEntryClass: Class<*>, application: Application?): Any {
        return applicationEntryClass.kotlin.objectInstance ?: run {
            val constructors = applicationEntryClass.kotlin.constructors.filter {
                it.parameters.all { p -> p.isOptional || isApplicationEnvironment(p) || (isApplication(p) && application != null) }
            }

            if (constructors.isEmpty()) {
                throw RuntimeException("There are no applicable constructors found in class $applicationEntryClass")
            }

            val constructor = constructors.bestFunction()
            callEntryPointFunction(null, constructor, application)
        }
    }

    private fun <R> List<KFunction<R>>.bestFunction() = sortedWith(compareBy({ it.parameters.count { !it.isOptional } }, { it.parameters.size })).last()

    private fun <R> callEntryPointFunction(instance: Any?, entryPoint: KFunction<R>, application: Application?): R {
        return entryPoint.callBy(entryPoint.parameters
                .filterNot { it.isOptional }
                .associateBy({ it }, { p ->
                    @Suppress("IMPLICIT_CAST_TO_ANY")
                    when {
                        p.kind == KParameter.Kind.INSTANCE -> instance
                        isApplicationEnvironment(p) -> environment
                        isApplication(p) -> application ?: throw IllegalArgumentException("Couldn't inject application instance to $entryPoint")
                        else -> throw RuntimeException("Parameter type ${p.type} of parameter ${p.name} is not supported")
                    }
                }))
    }

    private fun <T> List<T>.nullIfEmpty() = if (isEmpty()) null else this

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

        private val ApplicationFeatureClassInstance = ApplicationFeature::class.java
        private val ApplicationEnvironmentClassInstance = ApplicationEnvironment::class.java
        private val ApplicationClassInstance = Application::class.java
    }
}

