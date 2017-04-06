@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.tomcat

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    TomcatApplicationHost(applicationEnvironment).start(true)
}
