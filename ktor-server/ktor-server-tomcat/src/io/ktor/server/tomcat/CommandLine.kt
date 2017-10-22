@file:JvmName("DevelopmentHost")

package io.ktor.server.tomcat

import io.ktor.server.host.*

/**
 * Main function for starting DevelopmentHost with Tomcat
 * Creates an embedded Tomcat application with an environment built from command line arguments.
 */
fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    TomcatApplicationHost(applicationEnvironment, {}).start(true)
}
