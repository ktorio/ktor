@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    JettyApplicationHost(applicationEnvironment).start()
}
