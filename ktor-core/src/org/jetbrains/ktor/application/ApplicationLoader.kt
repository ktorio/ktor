package org.jetbrains.ktor.application

import com.sun.nio.file.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.*
import java.util.*

/** Controls the loading of a Ktor app from a directory.
 */
public class ApplicationLoader(val config: ApplicationConfig) {
    private var _applicationInstance: Application? = null
    private val applicationInstanceLock = Object()
    private val packageWatchKeys = ArrayList<WatchKey>()
    private val log = config.log.fork("Loader")

    public fun ApplicationConfig.isDevelopment(): Boolean = environment == "development"

    init {
        application // eagerly create application
    }

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    public val application: Application
        get() = synchronized(applicationInstanceLock) {
            if (config.isDevelopment()) {
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

    fun ClassLoader.allURLs(): List<URL> {
        val parentUrls = parent?.allURLs() ?: emptyList()
        if (this is URLClassLoader) {
            val urls = urLs.filterNotNull()
            log.debug("ClassLoader $this: $urls")
            return urls + parentUrls
        }
        return parentUrls
    }

    fun createApplication(): Application {
        val classLoader = if (config.isDevelopment()) {
            val allUrls = config.classLoader.allURLs()
            val watchPatterns = config.watchPatterns
            val watchUrls = allUrls.filter { url -> watchPatterns.any { pattern -> url.toString().contains(pattern) } }
            watchUrls(watchUrls)
            OverridingClassLoader(watchUrls, config.classLoader)
        } else
            config.classLoader

        val currentThread = Thread.currentThread()
        val oldThreadClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoader
        try {
            val applicationClass = classLoader.loadClass(config.applicationClassName)
                    ?: throw RuntimeException("Application class ${config.applicationClassName} cannot be loaded")
            log.debug("Application class: $applicationClass in ${applicationClass.classLoader}")
            val cons = applicationClass.getConstructor(ApplicationConfig::class.java)
            val application = cons.newInstance(config)
            if (application !is Application)
                throw RuntimeException("Application class ${config.applicationClassName} should inherit from ${Application::class}")
            return application
        } finally {
            currentThread.contextClassLoader = oldThreadClassLoader
        }
    }


    fun destroyApplication() {
        try {
            _applicationInstance?.dispose()
        } catch(e: Throwable) {
            log.error("Failed to destroy application instance.", e)
        }
        packageWatchKeys.forEach { it.cancel() }
        packageWatchKeys.clear()
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

        val watcher = FileSystems.getDefault().newWatchService();
        paths.forEach { path ->
            log.debug("Watching $path for changes.")
        }
        val modifiers = get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH()?.let { arrayOf(it) } ?: emptyArray()
        packageWatchKeys.addAll(paths.map {
            it.register(watcher, arrayOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), *modifiers)
        })
    }

    public fun dispose() {
        destroyApplication()
    }
}
