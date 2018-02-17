@file:JvmName("DevelopmentEngine")

package io.ktor.server.tomcat

import io.ktor.server.engine.*

/**
 * Main function for starting DevelopmentEngine with Tomcat
 * Creates an embedded Tomcat application with an environment built from command line arguments.
 */
fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    TomcatApplicationEngine(applicationEnvironment, {}).start(true)
}
