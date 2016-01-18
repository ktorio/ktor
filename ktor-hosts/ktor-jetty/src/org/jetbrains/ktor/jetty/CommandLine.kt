@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val (applicationHostConfig, applicationConfig) = commandLineConfig(args)
    JettyApplicationHost(applicationHostConfig, applicationConfig).start()
}
