package ktor.application

import java.net.*

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
        get() = if (classPathUrl == null) array() else array(classPathUrl)

    public val environment: String get() = tryGet("ktor.environment") ?: "development"

    public val applicationPackageName: String
        get() = get("ktor.application.package")

    public val applicationClassName: String
        get() = tryGet("ktor.application.class") ?: "$applicationPackageName.Application"

    public val hotPackages: List<String>
        get() = tryGet("ktor.application.hotPackages")?.split(',')?.toList()?.map { "${it.trim()}.*" }  ?: listOf<String>()

    public val staticPackages: List<String>
        get() = tryGet("ktor.application.staticPackages")?.split(',')?.toList()?.map { "${it.trim()}.*" }  ?: listOf<String>()

    /** Directories where publicly available files (like stylesheets, scripts, and images) will go. */
    public val publicDirectories: Array<String>
        get() = tryGet("ktor.application.static")?.split(';') ?: array<String>()

    /** The port to run the server on. */
    public val port: Int
        get() = (tryGet("ktor.application.port") ?: "8080").toInt()

}
