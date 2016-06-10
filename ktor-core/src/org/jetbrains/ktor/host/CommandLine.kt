package org.jetbrains.ktor.host

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import java.io.*
import java.net.*

fun commandLineConfig(args: Array<String>): Pair<ApplicationHostConfig, ApplicationEnvironment> {
    val argsMap = args.mapNotNull { it.splitPair('=') }.toMap()

    val jar = argsMap["-jar"]?.let { File(it).toURI().toURL() }
    val argConfig = ConfigFactory.parseMap(argsMap.filterKeys { it.startsWith("-P:") }.mapKeys { it.key.removePrefix("-P:") }, "Command-line options")
    val combinedConfig = argConfig.withFallback(ConfigFactory.load())

    val applicationIdPath = "ktor.application.id"

    val hostConfigPath = "ktor.deployment.host"
    val hostPortPath = "ktor.deployment.port"
    val hostReload = "ktor.deployment.autoreload"

    val applicationId = combinedConfig.tryGetString(applicationIdPath) ?: "Application"
    val log = SLF4JApplicationLog(applicationId)
    val classLoader = jar?.let { URLClassLoader(arrayOf(jar), ApplicationEnvironment::class.java.classLoader) }
            ?: ApplicationEnvironment::class.java.classLoader
    val appConfig = HoconApplicationConfig(combinedConfig)
    log.info(combinedConfig.getObject("ktor").render())

    val hostConfig = applicationHostConfig {
        (argsMap["-host"] ?: combinedConfig.tryGetString(hostConfigPath))?.let {
            host = it
        }
        (argsMap["-port"] ?: combinedConfig.tryGetString(hostPortPath))?.let {
            port = it.toInt()
        }
        (argsMap["-autoreload"] ?: combinedConfig.tryGetString(hostReload))?.let {
            autoreload = it.toBoolean()
        }
    }

    return hostConfig to BasicApplicationEnvironment(classLoader, log, appConfig)
}

private fun Config.tryGetString(path: String): String? = if (hasPath(path)) getString(path) else null

private fun String.splitPair(ch: Char): Pair<String, String>? = indexOf(ch).let { idx ->
    when (idx) {
        -1 -> null
        else -> Pair(take(idx), drop(idx + 1))
    }
}

