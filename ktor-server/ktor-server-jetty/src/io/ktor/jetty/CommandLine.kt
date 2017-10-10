@file:JvmName("DevelopmentHost")

package io.ktor.jetty

import io.ktor.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    JettyApplicationHost(applicationEnvironment).start()
}
