@file:JvmName("DevelopmentHost")

package io.ktor.server.netty

import io.ktor.server.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    NettyApplicationHost(applicationEnvironment).start()
}
