@file:JvmName("DevelopmentEngine")

package io.ktor.server.cio

import io.ktor.server.engine.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    CIOApplicationEngine(applicationEnvironment, {}).start(true)
}
