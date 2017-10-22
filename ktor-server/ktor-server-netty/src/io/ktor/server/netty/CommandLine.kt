@file:JvmName("DevelopmentHost")

package io.ktor.server.netty

import io.ktor.server.host.*

/**
 * Main function for starting DevelopmentHost with Netty
 * Creates an embedded Netty application with an environment built from command line arguments.
 */
fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    NettyApplicationHost(applicationEnvironment).start()
}
