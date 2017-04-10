package org.jetbrains.ktor.tests.jetty

import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.servlet.*
import org.jetbrains.ktor.testing.*
import javax.servlet.*

class JettyServletContainerHostTest : HostTestSuite<JettyServletApplicationHost>(ServletHostFactory)

// the factory and host are only suitable for testing
// you shouldn't use it for production code

object ServletHostFactory : ApplicationHostFactory<JettyServletApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment): JettyServletApplicationHost {
        return JettyServletApplicationHost(environment)
    }
}

class JettyServletApplicationHost(environment: ApplicationHostEnvironment,
                                  jettyServer: () -> Server = ::Server) : JettyApplicationHostBase(environment, jettyServer) {
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
