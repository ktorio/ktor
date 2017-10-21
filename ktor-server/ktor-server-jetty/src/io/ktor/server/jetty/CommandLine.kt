@file:JvmName("DevelopmentHost")

package io.ktor.server.jetty

import io.ktor.server.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    JettyApplicationHost(applicationEnvironment, {}).start()
}
