package io.ktor.server.tomcat

import io.ktor.server.engine.*

/**
 * Tomcat development engine
 */
object EngineMain {
    /**
     * Main function for starting DevelopmentEngine with Tomcat
     * Creates an embedded Tomcat application with an environment built from command line arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        TomcatApplicationEngine(applicationEnvironment, {}).start(true)
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Use EngineMain instead",
    replaceWith = ReplaceWith("EngineMain"),
    level = DeprecationLevel.HIDDEN
)
object DevelopmentEngine {
    @JvmStatic
    fun main(args: Array<String>) = EngineMain.main(args)
}
