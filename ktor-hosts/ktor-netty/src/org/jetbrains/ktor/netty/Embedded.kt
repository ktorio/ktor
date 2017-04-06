package org.jetbrains.ktor.netty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*

object Netty : ApplicationHostFactory<NettyApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = NettyApplicationHost(environment)
}

@Deprecated("Replace with 'embeddedServer(Netty, …)", replaceWith = ReplaceWith("embeddedServer(Netty, port, host, configure)", "org.jetbrains.ktor.host.embeddedServer"))
fun embeddedNettyServer(port: Int = 80, host: String = "0.0.0.0", main: Application.() -> Unit): NettyApplicationHost {
    return embeddedServer(Netty, port, host, main)
}
