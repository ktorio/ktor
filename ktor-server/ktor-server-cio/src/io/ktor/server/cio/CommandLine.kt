@file:JvmName("DevelopmentHost")

package io.ktor.server.cio

import io.ktor.server.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    CIOApplicationHost(applicationEnvironment, {}).start(true)
}
