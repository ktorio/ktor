package io.ktor.netty

import io.ktor.host.*

object Netty : ApplicationHostFactory<NettyApplicationHost, NettyApplicationHost.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: NettyApplicationHost.Configuration.() -> Unit): NettyApplicationHost {
        return NettyApplicationHost(environment, configure)
    }
}
