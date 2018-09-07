package io.ktor.tests.server.jetty

import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.servlet.*
import io.ktor.server.testing.*
import org.eclipse.jetty.servlet.*
import org.junit.*
import javax.servlet.*

class JettyAsyncServletContainerEngineTest :
    EngineTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))

class JettyBlockingServletContainerEngineTest :
    EngineTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false)) {
    @Ignore
    override fun testUpgrade() {}
}

// the factory and engine are only suitable for testing
// you shouldn't use it for production code

private class Servlet(private val async: Boolean) : ApplicationEngineFactory<JettyServletApplicationEngine, JettyApplicationEngineBase.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: JettyApplicationEngineBase.Configuration.() -> Unit): JettyServletApplicationEngine {
        return JettyServletApplicationEngine(environment, configure, async)
    }
}

@UseExperimental(EngineAPI::class)
private class JettyServletApplicationEngine(
    environment: ApplicationEngineEnvironment,
    configure: JettyApplicationEngineBase.Configuration.() -> Unit,
    async: Boolean
) : JettyApplicationEngineBase(environment, configure) {
    init {
        server.handler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            setAttribute(ServletApplicationEngine.ApplicationEngineEnvironmentAttributeKey, environment)

            insertHandler(ServletHandler().apply {
                val h = ServletHolder("ktor-servlet", ServletApplicationEngine::class.java).apply {
                    isAsyncSupported = async
                    registration.setLoadOnStartup(1)
                    registration.setMultipartConfig(MultipartConfigElement(System.getProperty("java.io.tmpdir")))
                    registration.setAsyncSupported(async)
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
