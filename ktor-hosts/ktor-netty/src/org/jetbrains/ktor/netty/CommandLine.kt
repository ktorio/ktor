@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.netty

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    NettyApplicationHost(applicationEnvironment).start()
}
