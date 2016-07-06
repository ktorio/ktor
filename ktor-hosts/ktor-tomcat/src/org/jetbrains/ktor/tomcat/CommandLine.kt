@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.tomcat

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val (applicationHostConfig, applicationEnvironment) = commandLineConfig(args)
    TomcatApplicationHost(applicationHostConfig, applicationEnvironment).start(true)
}
