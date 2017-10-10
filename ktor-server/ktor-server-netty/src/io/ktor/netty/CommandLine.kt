@file:JvmName("DevelopmentHost")

package io.ktor.netty

import io.ktor.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    NettyApplicationHost(applicationEnvironment).start()
}
