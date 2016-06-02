package org.jetbrains.ktor.servlet

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.logging.*
import javax.servlet.annotation.*

@MultipartConfig
open class ServletApplicationHost() : KtorServlet() {
    private val loader: ApplicationLoader by lazy {
        val servletContext = servletContext
        val parameterNames = servletContext.initParameterNames.toList().filter { it.startsWith("org.jetbrains.ktor") }
        val parameters = parameterNames.associateBy({ it.removePrefix("org.jetbrains.") }, { servletContext.getInitParameter(it) })

        val config = ConfigFactory.parseMap(parameters)
        val configPath = "ktor.config"

        val combinedConfig = if (config.hasPath(configPath)) {
            val configStream = servletContext.classLoader.getResourceAsStream(config.getString(configPath))
            val loadedKtorConfig = ConfigFactory.parseReader(configStream.bufferedReader())
            config.withFallback(loadedKtorConfig)
        } else
            config.withFallback(ConfigFactory.load())

        val applicationLog = SLF4JApplicationLog("ktor.application")
        val applicationConfig = HoconApplicationConfig(combinedConfig)
        val applicationEnvironment = BasicApplicationEnvironment(servletContext.classLoader, applicationLog, applicationConfig)
        ApplicationLoader(applicationEnvironment, false)
    }

    override val application: Application get() = loader.application

    override fun destroy() {
        executorService.shutdown()
        loader.dispose()
    }

}
