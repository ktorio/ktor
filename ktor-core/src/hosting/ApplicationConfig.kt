package ktor.application

import java.net.*
import com.typesafe.config.Config

/**
 * Store application configuration.
 */
public open class ApplicationConfig(
        public val config: Config,
        public val log: ApplicationLog = NullApplicationLog(),
        private val classPathUrl: URL? = null
                                   )
: Config by config {

    public open val classPath: Array<URL>
        get() = if (classPathUrl == null) arrayOf() else arrayOf(classPathUrl)

    public val environment: String get() = getString("ktor.environment")
    public val applicationPackageName: String = getString("ktor.application.package")
    public val applicationClassName: String = getString("ktor.application.class")

    /** Directories where publicly available files (like stylesheets, scripts, and images) will go. */
    public val publicDirectories: List<String> = getStringList("ktor.application.folders.public")

    /** The port to run the server on. */
    public val port: Int = getInt("ktor.application.port")

}
