package ktor.application.jetty

import ktor.application.*
import java.util.*
import java.net.*
import java.io.File
import javax.naming.InitialContext
import com.typesafe.config.ConfigFactory

fun main(args: Array<String>) {
    val map = HashMap<String, String>()
    for (arg in args) {
        val data = arg.split('=')
        if (data.size() == 2) {
            map[data[0]] = data[1]
        }
    }

    val jar = map["-jar"]?.let { File(it).toURI().toURL() }
    val classPath = if (jar == null) array<URL>() else array<URL>(jar)

    val namingContext = InitialContext()
    //val namingConfig = ConfigFactory.parseMap(namingContext.getEnvironment() as Map<String, out Any>)
    val applicationConfig = ConfigFactory.load()
    val commandLineConfig = ConfigFactory.parseMap(mapOf("ktor.environment" to (map["-env"] ?: "development")))
    //config.loadJsonResourceConfig(classPath)

    val combinedConfig = applicationConfig.withFallback(commandLineConfig)
    val log = SL4JApplicationLog("<Application>")
    val appConfig = ApplicationConfig(combinedConfig, log, jar)

    println(combinedConfig.getObject("ktor").render())
    JettyApplicationHost(appConfig).start()
}
