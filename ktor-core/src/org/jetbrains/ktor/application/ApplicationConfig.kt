package org.jetbrains.ktor.application

import com.typesafe.config.*
import java.net.*

/**
 * Store application configuration.
 */
public open class ApplicationConfig(private val config: Config,
                                    public val log: ApplicationLog = NullApplicationLog(),
                                    private val classPathUrl: URL? = null) {

    public open val classPath: Array<URL>
        get() = if (classPathUrl == null) arrayOf() else arrayOf(classPathUrl)

    public val classLoader: URLClassLoader = URLClassLoader(classPath, javaClass.getClassLoader())

    public val environment: String get() = config.getString("ktor.environment")
    public val applicationClassName: String = config.getString("ktor.application.class")

    /** Directories where publicly available files (like stylesheets, scripts, and images) will go. */
    public val publicDirectories: List<String> = config.getStringListOrEmpty("ktor.application.folders.public")

    /** The port to run the server on. */
    public val port: Int = config.getIntOrDefault("ktor.application.port", 80)

    public fun get(configuration: String): String = config.getString(configuration)

    private fun Config.getStringListOrEmpty(path: String): List<String> =
            if (hasPath(path))
                getStringList(path)
            else
                emptyList()

    private fun Config.getIntOrDefault(path: String, default: Int): Int =
            if (hasPath(path))
                getInt(path)
            else
                default
}

