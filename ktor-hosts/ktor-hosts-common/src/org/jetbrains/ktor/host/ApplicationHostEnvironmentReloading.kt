package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.util.*
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
 * When [reloadPackages] is `true`, it watches changes in folder/jar and implements hot reloading
 */
class ApplicationHostEnvironmentReloading(
        override val classLoader: ClassLoader,
        override val log: ApplicationLog,
        override val config: ApplicationConfig,
        override val connectors: List<HostConnectorConfig>,
        val modules: List<Application.() -> Unit>,
        val reloadPackages: List<String> = emptyList()
)
    : ApplicationHostEnvironment {

    private var _applicationInstance: Application? = null
    private val applicationInstanceLock = ReentrantReadWriteLock()
    private var packageWatchKeys = emptyList<WatchKey>()

    private val watchPatterns: List<String> = (config.propertyOrNull("ktor.deployment.watch")?.getList() ?: listOf()) + reloadPackages

    private val moduleFunctionNames: List<String>? = run {
        val configModules = config.propertyOrNull("ktor.application.modules")?.getList()
        if (watchPatterns.isEmpty()) configModules
        else {
            val unlinkedModules = modules.map {
                val fn = (it as? KFunction<*>)?.javaMethod ?: throw RuntimeException("Module function provided as lambda cannot be unlinked for reload")
                val clazz = fn.declaringClass
                val name = fn.name
                "${clazz.name}.$name"
            }
            if (configModules == null)
                unlinkedModules
            else
                configModules + unlinkedModules
        }
    }

    private val watcher by lazy { FileSystems.getDefault().newWatchService() }

    override val monitor = ApplicationMonitor()

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    override val application: Application
        get() = currentApplication()


    private fun currentApplication(): Application = applicationInstanceLock.read {
        if (watchPatterns.isNotEmpty()) {
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
                applicationInstanceLock.write {
                    destroyApplication()
                    _applicationInstance = createApplication()
                }
            }
        }

        _applicationInstance ?: throw IllegalStateException("ApplicationHostEnvironment was not started")
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
            return instantiateAndConfigureApplication(classLoader)
        } finally {
            currentThread.contextClassLoader = oldThreadClassLoader
        }
    }

    private fun createClassLoader(): ClassLoader {
        val baseClassLoader = classLoader
        if (watchPatterns.isEmpty())
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

        if (watchUrls.isEmpty()) {
            log.warning("No ktor.deployment.watch patterns match classpath entries, hot reload is disabled")
            return baseClassLoader
        }

        watchUrls(watchUrls)
        return OverridingClassLoader(watchUrls, baseClassLoader)
    }

    fun safeRiseEvent(event: Event<Application>, application: Application) {
        try {
            event(application)
        } catch(e: Throwable) {
            log.error("One or more of the handlers thrown an exception", e)
        }
    }

    fun destroyApplication() {
        val currentApplication = _applicationInstance
        if (currentApplication != null) {
            safeRiseEvent(monitor.applicationStopping, currentApplication)
            try {
                currentApplication.dispose()
            } catch(e: Throwable) {
                log.error("Failed to destroy application instance.", e)
            }

            safeRiseEvent(monitor.applicationStopped, currentApplication)
        }
        _applicationInstance = null
        packageWatchKeys.forEach { it.cancel() }
        packageWatchKeys = mutableListOf()
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
        applicationInstanceLock.write {
            _applicationInstance = createApplication()
        }
    }

    override fun stop() {
        applicationInstanceLock.write {
            destroyApplication()
        }
        if (watchPatterns.isNotEmpty()) {
            watcher.close()
        }
    }

    private fun instantiateAndConfigureApplication(classLoader: ClassLoader): Application {
        val application = Application(this)
        safeRiseEvent(monitor.applicationStarting, application)

        moduleFunctionNames?.forEach { fqName ->
            executeModuleFunction(classLoader, fqName, application)
        }

        if (watchPatterns.isEmpty()) {
            modules.forEach { it(application) }
        }

        safeRiseEvent(monitor.applicationStarted, application)
        return application
    }

    private fun executeModuleFunction(classLoader: ClassLoader, fqName: String, application: Application) {
        fqName.lastIndexOfAny(".#".toCharArray()).let { idx ->
            if (idx == -1) return@let
            val className = fqName.substring(0, idx)
            val functionName = fqName.substring(idx + 1)
            val clazz = classLoader.loadClassOrNull(className) ?: return@let

            val staticFunctions = clazz.methods
                    .filter { it.name == functionName && Modifier.isStatic(it.modifiers) }
                    .mapNotNull { it.kotlinFunction }

            staticFunctions.bestFunction()?.let { moduleFunction ->
                callFunctionWithInjection(null, moduleFunction, application)
                return
            }

            if (Function1::class.java.isAssignableFrom(clazz)) {
                val constructor = clazz.declaredConstructors.single()
                if (constructor.parameterCount != 0) {
                    throw RuntimeException("Module function with captured variables cannot be instantiated '$fqName'")
                }
                constructor.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val function = constructor.newInstance() as Function1<Application, Unit>
                function(application)
                return
            }

            val kclass = clazz.kotlin
            val instance = createModuleContainer(kclass, application)
            kclass.functions.filter { it.name == functionName }.bestFunction()?.let { moduleFunction ->
                callFunctionWithInjection(instance, moduleFunction, application)
                return
            }
        }

        throw ClassNotFoundException("Module function cannot be found for the fully qualified name '$fqName'")
    }

    private fun createModuleContainer(applicationEntryClass: KClass<*>, application: Application): Any {
        val objectInstance = applicationEntryClass.objectInstance
        if (objectInstance != null) return objectInstance

        val constructors = applicationEntryClass.constructors.filter {
            it.parameters.all { p -> p.isOptional || isApplicationEnvironment(p) || isApplication(p) }
        }

        val constructor = constructors.bestFunction() ?: throw RuntimeException("There are no applicable constructors found in class $applicationEntryClass")
        return callFunctionWithInjection(null, constructor, application)
    }

    private fun <R> List<KFunction<R>>.bestFunction(): KFunction<R>? {
        return sortedWith(compareBy({ it.parameters.count { !it.isOptional } }, { it.parameters.size })).lastOrNull()
    }

    private fun <R> callFunctionWithInjection(instance: Any?, entryPoint: KFunction<R>, application: Application): R {
        return entryPoint.callBy(entryPoint.parameters
                .filterNot { it.isOptional }
                .associateBy({ it }, { p ->
                    @Suppress("IMPLICIT_CAST_TO_ANY")
                    when {
                        p.kind == KParameter.Kind.INSTANCE -> instance
                        isApplicationEnvironment(p) -> this
                        isApplication(p) -> application
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
