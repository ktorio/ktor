package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*

object Jetty : ApplicationHostFactory<JettyApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = JettyApplicationHost(environment)
}

@Deprecated("Replace with 'embeddedServer(Jetty, …)", replaceWith = ReplaceWith("embeddedServer(Jetty, port, host, configure)", "org.jetbrains.ktor.host.embeddedServer"))
fun embeddedJettyServer(port: Int = 80, host: String = "0.0.0.0", main: Application.() -> Unit): JettyApplicationHost {
    return embeddedServer(Jetty, port, host, main)
}
