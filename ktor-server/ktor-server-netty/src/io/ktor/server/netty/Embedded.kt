package io.ktor.server.netty

import io.ktor.server.host.*

object Netty : ApplicationHostFactory<NettyApplicationHost, NettyApplicationHost.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: NettyApplicationHost.Configuration.() -> Unit): NettyApplicationHost {
        return NettyApplicationHost(environment, configure)
    }
}
