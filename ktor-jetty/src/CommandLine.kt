package ktor.application.jetty

import com.typesafe.config.ConfigFactory
import ktor.application.ApplicationConfig
import ktor.application.SL4JApplicationLog
import java.io.File
import java.net.URL
import java.util.HashMap
import javax.naming.InitialContext

fun main(args: Array<String>) {
    val map = HashMap<String, String>()
    for (arg in args) {
        val data = arg.split('=')
        if (data.size() == 2) {
            map[data[0]] = data[1]
        }
    }

    val jar = map["-jar"]?.let { File(it).toURI().toURL() }
    val classPath = if (jar == null) arrayOf<URL>() else arrayOf<URL>(jar)

    val namingContext = InitialContext()
    //val namingConfig = ConfigFactory.parseMap(namingContext.getEnvironment() as Map<String, out Any>)
    val applicationConfig = ConfigFactory.load()
    val commandLineConfig = ConfigFactory.parseMap(mapOf("ktor.environment" to (map["-env"] ?: "development")))

    val combinedConfig = applicationConfig.withFallback(commandLineConfig)
    val log = SL4JApplicationLog("<Application>")
    val appConfig = ApplicationConfig(combinedConfig, log, jar)

    println(combinedConfig.getObject("ktor").render())
    JettyApplicationHost(appConfig).start()
}
