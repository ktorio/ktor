package io.ktor.tests.server.jetty

import io.ktor.host.*
import io.ktor.jetty.*
import io.ktor.servlet.*
import io.ktor.testing.*
import org.eclipse.jetty.servlet.*
import javax.servlet.*

class JettyServletContainerHostTest : HostTestSuite<JettyApplicationHostBase, JettyApplicationHostBase.Configuration>(ServletHostFactory)

// the factory and host are only suitable for testing
// you shouldn't use it for production code

private object ServletHostFactory : ApplicationHostFactory<JettyServletApplicationHost, JettyApplicationHostBase.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: JettyApplicationHostBase.Configuration.() -> Unit): JettyServletApplicationHost {
        return JettyServletApplicationHost(environment, configure)
    }
}

private class JettyServletApplicationHost(environment: ApplicationHostEnvironment, configure: JettyApplicationHostBase.Configuration.() -> Unit) : JettyApplicationHostBase(environment, configure) {
    init {
        server.handler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            setAttribute(ServletApplicationHost.ApplicationHostEnvironmentAttributeKey, environment)

            insertHandler(ServletHandler().apply {
                val h = ServletHolder("ktor-servlet", ServletApplicationHost::class.java).apply {
                    isAsyncSupported = true
                    registration.setLoadOnStartup(1)
                    registration.setMultipartConfig(MultipartConfigElement(System.getProperty("java.io.tmpdir")))
                    registration.setAsyncSupported(true)
                }

                addServlet(h)
                addServletMapping(ServletMapping().apply {
                    pathSpecs = arrayOf("*.", "/*")
                    servletName = "ktor-servlet"
                })
            })
        }
    }
}
