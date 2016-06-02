@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.tomcat

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val (applicationHostConfig, applicationConfig) = commandLineConfig(args)
    TomcatApplicationHost(applicationHostConfig, applicationConfig).start(true)
}
