package ktor.application

import java.net.*
import java.util.regex.Pattern
import java.util.*
import kotlin.properties.*
import java.nio.file.*

import java.io.File
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.BasicFileAttributes

/** Controls the loading of a Ktor app from a directory.
 */
public class ApplicationLoader(val config: ApplicationConfig)  {
    private var _applicationInstance: Application? = null
    private val applicationInstanceLock = Object()
    private val packageWatchKeys = ArrayList<WatchKey>()

    public fun ApplicationConfig.isDevelopment(): Boolean = environment == "development"

    public val application: Application
        get() = synchronized(applicationInstanceLock) {
            if (config.isDevelopment()) {
                val changes = packageWatchKeys.flatMap { it.pollEvents()!! }
                if (changes.size() > 0) {
                    config.log.info("Changes in application detected.")
                    var count = changes.size()
                    while (true) {
                        Thread.sleep(200)
                        val moreChanges = packageWatchKeys.flatMap { it.pollEvents()!! }
                        if (moreChanges.size() == 0)
                            break
                        config.log.debug("Waiting for more changes.")
                        count += moreChanges.size()
                    }

                    config.log.debug("Changes to ${count} files caused application restart.")
                    changes.take(5).forEach { config.log.debug("...  ${it.context()}") }
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

    public fun requestClassloader(current: ClassLoader): ClassLoader =
            if (config.isDevelopment()) {
                URLClassLoader(config.classPath, current)
            } else
                current

    fun createApplication(): Application {
        if (config.isDevelopment())
            watchUrls(config.classPath)

        val defaultClassLoader = javaClass.getClassLoader()!!
        val classLoader = if (config.isDevelopment())
            URLClassLoader(config.classPath, defaultClassLoader)
        else
            defaultClassLoader

        val appClassObject = classLoader.loadClass(config.applicationClassName)
        if (appClassObject == null)
            throw RuntimeException("Expected class ${config.applicationClassName} to be defined")
        val applicationClass = appClassObject as Class<Application>
        val cons = applicationClass.getConstructor(javaClass<ApplicationConfig>(), javaClass<ClassLoader>())
        val application = cons.newInstance(config, classLoader)
        if (application !is Application)
            throw RuntimeException("Expected class ${config.applicationClassName} to be inherited from Application")

        config.log.debug("Application class: ${application.javaClass.toString()}")
        return application
    }


    fun destroyApplication() {
        try {
            _applicationInstance?.dispose()
        } catch(e: Throwable) {
            println("Failed to destroy application instance.")
            println(e.getMessage())
            println(e.printStackTrace())
        }
        packageWatchKeys.forEach { it.cancel() }
        packageWatchKeys.clear()
    }

    fun watchUrls(urls: Array<URL>) {
        val paths = HashSet<Path>()
        for (url in urls) {
            val path = url.getPath()
            if (path != null) {
                val folder = File(URLDecoder.decode(path, "utf-8")).toPath()
                val visitor = object : SimpleFileVisitor<Path?>() {
                    override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes): FileVisitResult {
                        paths.add(dir!!)
                        return FileVisitResult.CONTINUE
                    }
                    override fun visitFile(file: Path?, attrs: BasicFileAttributes): FileVisitResult {
                        val dir = file?.getParent()
                        if (dir != null)
                            paths.add(dir)
                        return FileVisitResult.CONTINUE
                    }
                }
                Files.walkFileTree(folder, visitor)
            }
        }

        val watcher = FileSystems.getDefault()!!.newWatchService();
        paths.forEach {
            config.log.debug("Watching ${it} for changes.")
        }
        packageWatchKeys.addAll(paths.map { it.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)!! })
    }

    public fun dispose() {
        destroyApplication()
    }

    class RestrictedClassLoader(restrictions: List<String>, allows: List<String>, urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
        val restrictor: Pattern = Pattern.compile(restrictions.map { it.replace(".", "\\.").replace("*", ".*") }.makeString("|"))
        val allower: Pattern = Pattern.compile(allows.map { it.replace(".", "\\.").replace("*", ".*") }.makeString("|"))

        protected override fun findClass(name: String): Class<out Any?> {
            if (!allower.matcher(name).matches() && restrictor.matcher(name).matches()) {
                throw ClassNotFoundException("$name is in hot package")
            }
            return super<URLClassLoader>.findClass(name)
        }
    }
}
