package io.ktor.server.servlet

import com.typesafe.config.*
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.host.*
import org.slf4j.*
import javax.servlet.annotation.*

@MultipartConfig
open class ServletApplicationHost : KtorServlet() {
    private val environment: ApplicationHostEnvironment by lazy {
        val servletContext = servletContext

        servletContext.getAttribute(ApplicationHostEnvironmentAttributeKey)?.let { return@lazy it as ApplicationHostEnvironment }

        val parameterNames = servletContext.initParameterNames.toList().filter { it.startsWith("io.ktor") }
        val parameters = parameterNames.associateBy({ it.removePrefix("io.ktor.") }, { servletContext.getInitParameter(it) })

        val hocon = ConfigFactory.parseMap(parameters)
        val configPath = "ktor.config"
        val applicationIdPath = "ktor.application.id"

        val combinedConfig = if (hocon.hasPath(configPath)) {
            val configStream = servletContext.classLoader.getResourceAsStream(hocon.getString(configPath))
            val loadedKtorConfig = ConfigFactory.parseReader(configStream.bufferedReader())
            hocon.withFallback(loadedKtorConfig)
        } else
            hocon.withFallback(ConfigFactory.load())

        val applicationId = combinedConfig.tryGetString(applicationIdPath) ?: "Application"

        applicationHostEnvironment {
            config = HoconApplicationConfig(combinedConfig)
            log = LoggerFactory.getLogger(applicationId)
            classLoader = servletContext.classLoader
        }.apply {
            monitor.subscribe(ApplicationStarting)  {
                it.receivePipeline.installDefaultTransformations()
                it.sendPipeline.installDefaultTransformations()
            }
        }
    }

    override val application: Application get() = environment.application
    override val hostPipeline by lazy { defaultHostPipeline(environment) }
    override val upgrade: ServletUpgrade by lazy {
        if ("jetty" in servletContext.serverInfo?.toLowerCase() ?: "") {
            jettyUpgrade ?: DefaultServletUpgrade
        } else DefaultServletUpgrade
    }

    override fun init() {
        environment.start()
        super.init()
    }

    override fun destroy() {
        super.destroy()
        environment.stop()
    }

    companion object {
        val ApplicationHostEnvironmentAttributeKey = "_ktor_application_host_environment_instance"
        private val jettyUpgrade by lazy {
            try {
                Class.forName("io.ktor.server.jetty.internal.JettyUpgradeImpl").kotlin.objectInstance as ServletUpgrade
            } catch (t: Throwable) {
                null
            }
        }
    }
}
