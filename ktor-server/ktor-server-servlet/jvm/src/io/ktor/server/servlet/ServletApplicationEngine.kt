package io.ktor.server.servlet

import com.typesafe.config.*
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import org.slf4j.*
import javax.servlet.*
import javax.servlet.annotation.*

/**
 * This servlet need to be installed into a servlet container
 */
@MultipartConfig
open class ServletApplicationEngine : KtorServlet() {
    private val environment: ApplicationEngineEnvironment by lazy {
        val servletContext = servletContext
        val servletConfig = servletConfig

        servletContext.getAttribute(ApplicationEngineEnvironmentAttributeKey)?.let { return@lazy it as ApplicationEngineEnvironment }

        val parameterNames = (servletContext.initParameterNames?.toList().orEmpty() +
            servletConfig.initParameterNames?.toList().orEmpty()).filter { it.startsWith("io.ktor") }.distinct()
        val parameters = parameterNames.associateBy({ it.removePrefix("io.ktor.") }, {
            servletConfig.getInitParameter(it) ?: servletContext.getInitParameter(it)
        })

        val hocon = ConfigFactory.parseMap(parameters)
        val configPath = "ktor.config"
        val applicationIdPath = "ktor.application.id"

        val combinedConfig = if (hocon.hasPath(configPath)) {
            val configStream = servletContext.classLoader.getResourceAsStream(hocon.getString(configPath))
                ?: throw ServletException("No config ${hocon.getString(configPath)} found for the servlet named $servletName")
            val loadedKtorConfig = configStream.bufferedReader().use { ConfigFactory.parseReader(it) }
            hocon.withFallback(loadedKtorConfig)
        } else
            hocon.withFallback(ConfigFactory.load())

        val applicationId = combinedConfig.tryGetString(applicationIdPath) ?: "Application"

        applicationEngineEnvironment {
            config = HoconApplicationConfig(combinedConfig)
            log = LoggerFactory.getLogger(applicationId)
            classLoader = servletContext.classLoader
            rootPath = servletContext.contextPath ?: "/"
        }.apply {
            monitor.subscribe(ApplicationStarting)  {
                it.receivePipeline.merge(enginePipeline.receivePipeline)
                it.sendPipeline.merge(enginePipeline.sendPipeline)
                it.receivePipeline.installDefaultTransformations()
                it.sendPipeline.installDefaultTransformations()
            }
        }
    }

    override val application: Application get() = environment.application

    override val enginePipeline: EnginePipeline by lazy {
        defaultEnginePipeline(environment).also {
            BaseApplicationResponse.setupSendPipeline(it.sendPipeline)
        }
    }

    override val upgrade: ServletUpgrade by lazy {
        if ("jetty" in servletContext.serverInfo?.toLowerCase() ?: "") {
            jettyUpgrade ?: DefaultServletUpgrade
        } else DefaultServletUpgrade
    }

    /**
     * Called by the servlet container when loading the servlet (on load)
     */
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
        /**
         * An application engine instance key. It is not recommended to use unless you are writing
         * your own servlet application engine implementation
         */
        @EngineAPI
        const val ApplicationEngineEnvironmentAttributeKey: String = "_ktor_application_engine_environment_instance"

        private val jettyUpgrade by lazy {
            try {
                Class.forName("io.ktor.server.jetty.internal.JettyUpgradeImpl").kotlin.objectInstance as ServletUpgrade
            } catch (t: Throwable) {
                null
            }
        }
    }
}
