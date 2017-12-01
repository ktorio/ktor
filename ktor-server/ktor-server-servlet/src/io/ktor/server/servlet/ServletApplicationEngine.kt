package io.ktor.server.servlet

import com.typesafe.config.*
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import org.slf4j.*
import javax.servlet.annotation.*

@MultipartConfig
open class ServletApplicationEngine : KtorServlet() {
    private val environment: ApplicationEngineEnvironment by lazy {
        val servletContext = servletContext

        servletContext.getAttribute(ApplicationEngineEnvironmentAttributeKey)?.let { return@lazy it as ApplicationEngineEnvironment }

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

        applicationEngineEnvironment {
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
    override val enginePipeline by lazy { defaultEnginePipeline(environment) }
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
        environment.monitor.raise(ApplicationStopPreparing, environment)
        super.destroy()
        environment.stop()
    }

    companion object {
        val ApplicationEngineEnvironmentAttributeKey = "_ktor_application_engine_environment_instance"
        private val jettyUpgrade by lazy {
            try {
                Class.forName("io.ktor.server.jetty.internal.JettyUpgradeImpl").kotlin.objectInstance as ServletUpgrade
            } catch (t: Throwable) {
                null
            }
        }
    }
}
