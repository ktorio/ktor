@file:JvmName("DevelopmentHost")

package io.ktor.server.jetty

import io.ktor.server.host.*

/**
 * Main function for starting DevelopmentHost with Jetty
 * Creates an embedded Jetty application with an environment built from command line arguments.
 */
fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    JettyApplicationHost(applicationEnvironment, {}).start()
}
