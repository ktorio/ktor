package org.jetbrains.ktor.tomcat

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*

object Tomcat : ApplicationHostFactory<TomcatApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = TomcatApplicationHost(environment)
}

@Deprecated("Replace with 'embeddedServer(Tomcat, …)", replaceWith = ReplaceWith("embeddedServer(Tomcat, port, host, configure)", "org.jetbrains.ktor.host.embeddedServer"))
fun embeddedTomcatServer(port: Int = 80, host: String = "0.0.0.0", main: Application.() -> Unit): TomcatApplicationHost {
    return embeddedServer(Tomcat, port, host, module = main)
}
