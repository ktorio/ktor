@file:JvmName("DevelopmentEngine")

package io.ktor.server.netty

import io.ktor.server.engine.*

/**
 * Main function for starting DevelopmentEngine with Netty
 * Creates an embedded Netty application with an environment built from command line arguments.
 */
fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    NettyApplicationEngine(applicationEnvironment).start()
}
