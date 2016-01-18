@file:JvmName("DevelopmentHost")

package org.jetbrains.ktor.netty

import org.jetbrains.ktor.launcher.*

fun main(args: Array<String>) {
    NettyApplicationHost(buildDefaultConfig(args)).start()
}
