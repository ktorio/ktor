package org.jetbrains.ktor.netty

import org.jetbrains.ktor.host.*

object Netty : ApplicationHostFactory<NettyApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = NettyApplicationHost(environment)
}
