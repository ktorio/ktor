package io.ktor.netty

import io.ktor.host.*

object Netty : ApplicationHostFactory<NettyApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = NettyApplicationHost(environment)
}
