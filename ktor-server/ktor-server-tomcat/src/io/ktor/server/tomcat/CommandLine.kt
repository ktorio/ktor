@file:JvmName("DevelopmentHost")

package io.ktor.server.tomcat

import io.ktor.server.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    TomcatApplicationHost(applicationEnvironment, {}).start(true)
}
