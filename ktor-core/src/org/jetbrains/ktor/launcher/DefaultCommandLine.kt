package org.jetbrains.ktor.launcher

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import java.io.*

fun buildDefaultConfig(args: Array<String>): ApplicationConfig {
    val argsMap = args.map { it.splitPair('=') }.filterNotNull().toMap()

    val jar = argsMap["-jar"]?.let { File(it).toURI().toURL() }
    val argConfig = ConfigFactory.parseMap(argsMap.filterKeys { it.startsWith("-P:") }.mapKeys { it.key.removePrefix("-P:") }, "Command-line options")
    val combinedConfig = argConfig.withFallback(ConfigFactory.load())

    val log = SL4JApplicationLog("<Application>")
    val appConfig = ApplicationConfig(combinedConfig, log, jar)
    log.info(combinedConfig.getObject("ktor").render())

    return appConfig
}

private fun String.splitPair(ch: Char): Pair<String, String>? = indexOf(ch).let { idx ->
    when (idx) {
        -1 -> null
        else -> Pair(take(idx), drop(idx + 1))
    }
}

