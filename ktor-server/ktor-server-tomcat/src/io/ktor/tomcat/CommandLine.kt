@file:JvmName("DevelopmentHost")

package io.ktor.tomcat

import io.ktor.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    TomcatApplicationHost(applicationEnvironment, {}).start(true)
}
