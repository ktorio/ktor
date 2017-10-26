package io.ktor.tests.server.jetty

import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.servlet.*
import io.ktor.server.testing.*
import org.eclipse.jetty.servlet.*
import javax.servlet.*

class JettyServletContainerEngineTest : EngineTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet)

// the factory and engine are only suitable for testing
// you shouldn't use it for production code

private object Servlet : ApplicationEngineFactory<JettyServletApplicationEngine, JettyApplicationEngineBase.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: JettyApplicationEngineBase.Configuration.() -> Unit): JettyServletApplicationEngine {
        return JettyServletApplicationEngine(environment, configure)
    }
}

private class JettyServletApplicationEngine(environment: ApplicationEngineEnvironment, configure: JettyApplicationEngineBase.Configuration.() -> Unit) : JettyApplicationEngineBase(environment, configure) {
    init {
        server.handler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            setAttribute(ServletApplicationEngine.ApplicationEngineEnvironmentAttributeKey, environment)

            insertHandler(ServletHandler().apply {
                val h = ServletHolder("ktor-servlet", ServletApplicationEngine::class.java).apply {
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
