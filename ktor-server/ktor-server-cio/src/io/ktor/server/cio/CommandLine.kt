@file:JvmName("DevelopmentEngine")

package io.ktor.server.cio

import io.ktor.server.engine.*
import java.util.concurrent.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    val engine = CIOApplicationEngine(applicationEnvironment, {})
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            engine.stop(3, 5, TimeUnit.SECONDS)
        }
    })
    engine.start(true)
}
