package org.jetbrains.ktor.jetty

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import java.io.*
import java.net.*
import java.util.*
import javax.naming.*

fun main(args: Array<String>) {
    val map = HashMap<String, String>()
    for (arg in args) {
        val data = arg.split('=')
        if (data.size() == 2) {
            map[data[0]] = data[1]
        }
    }

    val jar = map["-jar"]?.let { File(it).toURI().toURL() }
    val combinedConfig = ConfigFactory.load()
    val log = SL4JApplicationLog("<Application>")
    val appConfig = ApplicationConfig(combinedConfig, log, jar)

    println(combinedConfig.getObject("ktor").render())
    JettyApplicationHost(appConfig).start()
}
