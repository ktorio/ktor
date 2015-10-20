package org.jetbrains.ktor.application

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

    fun URLClassLoader.allURLs() : List<URL> {
        val parent = parent ?: return urLs.toList()
        if (parent is URLClassLoader)
            return urLs.toList() + parent.allURLs()
        return emptyList()
    }

    fun createApplication(): Application {
        val classLoader = config.classLoader
        if (config.isDevelopment()) {
            if (classLoader is URLClassLoader) {
                watchUrls(classLoader.allURLs() + config.classPath)
            } else
                watchUrls(config.classPath)
        }

        val applicationClass = classLoader.loadClass(config.applicationClassName)
                ?: throw RuntimeException("Expected class ${config.applicationClassName} to be defined")
        log.debug("Application class: ${applicationClass.toString()}")
        val cons = applicationClass.getConstructor(ApplicationConfig::class.java)
        val application = cons.newInstance(config)
        if (application !is Application)
            throw RuntimeException("Expected class ${config.applicationClassName} to be inherited from Application")

        return application
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
        packageWatchKeys.addAll(paths.map { it.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) })
    }

    public fun dispose() {
        destroyApplication()
    }
}
