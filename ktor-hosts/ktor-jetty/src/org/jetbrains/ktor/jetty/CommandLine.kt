@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val (applicationHostConfig, applicationEnvironment) = commandLineConfig(args)
    JettyApplicationHost(applicationHostConfig, applicationEnvironment).start()
}
