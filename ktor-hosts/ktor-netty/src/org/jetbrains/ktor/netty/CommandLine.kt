@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.netty

import org.jetbrains.ktor.host.*

fun main(args: Array<String>) {
    val (applicationHostConfig, applicationConfig) = commandLineConfig(args)
    NettyApplicationHost(applicationHostConfig, applicationConfig).start()
}
