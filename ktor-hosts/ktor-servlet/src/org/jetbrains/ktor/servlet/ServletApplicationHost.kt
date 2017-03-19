package org.jetbrains.ktor.servlet

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.transform.*
import javax.servlet.annotation.*

@MultipartConfig
open class ServletApplicationHost() : KtorServlet() {
    private val lifecycle: ApplicationLifecycleReloading by lazy {
        val servletContext = servletContext
        val parameterNames = servletContext.initParameterNames.toList().filter { it.startsWith("org.jetbrains.ktor") }
        val parameters = parameterNames.associateBy({ it.removePrefix("org.jetbrains.") }, { servletContext.getInitParameter(it) })

        val hocon = ConfigFactory.parseMap(parameters)
        val configPath = "ktor.config"

        val combinedConfig = if (hocon.hasPath(configPath)) {
            val configStream = servletContext.classLoader.getResourceAsStream(hocon.getString(configPath))
            val loadedKtorConfig = ConfigFactory.parseReader(configStream.bufferedReader())
            hocon.withFallback(loadedKtorConfig)
        } else
            hocon.withFallback(ConfigFactory.load())

        val log = SLF4JApplicationLog("ktor.application")
        val config = HoconApplicationConfig(combinedConfig)
        val environment = BasicApplicationEnvironment(servletContext.classLoader, log, config)
        environment.monitor.applicationStart += {
            it.install(ApplicationTransform).registerDefaultHandlers()
        }
        ApplicationLifecycleReloading(environment, false)
    }

    override val application: Application get() = lifecycle.application
    override fun init() {
        lifecycle.start()
        super.init()
    }

    override fun destroy() {
        super.destroy()
        lifecycle.stop()
    }
}
