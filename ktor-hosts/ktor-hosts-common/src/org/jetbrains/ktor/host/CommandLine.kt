package org.jetbrains.ktor.host

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.slf4j.*
import java.io.*
import java.net.*
import java.security.*

/**
 * Creates an [ApplicationHostEnvironment] instance from command line arguments
 */
fun commandLineEnvironment(args: Array<String>): ApplicationHostEnvironment {
    val argsMap = args.mapNotNull { it.splitPair('=') }.toMap()

    val jar = argsMap["-jar"]?.let { File(it).toURI().toURL() }
    val configFile = argsMap["-config"]?.let { File(it) }
    val commandLineMap = argsMap.filterKeys { it.startsWith("-P:") }.mapKeys { it.key.removePrefix("-P:") }

    val environmentConfig = ConfigFactory.systemProperties().withOnlyPath("ktor")
    val fileConfig = configFile?.let { ConfigFactory.parseFile(it) } ?: ConfigFactory.load()
    val argConfig = ConfigFactory.parseMap(commandLineMap, "Command-line options")
    val combinedConfig = argConfig.withFallback(fileConfig).withFallback(environmentConfig)

    val applicationIdPath = "ktor.application.id"

    val hostConfigPath = "ktor.deployment.host"
    val hostPortPath = "ktor.deployment.port"
    val hostWatchPaths = "ktor.deployment.watch"

    val hostSslPortPath = "ktor.deployment.sslPort"
    val hostSslKeyStore = "ktor.security.ssl.keyStore"
    val hostSslKeyAlias = "ktor.security.ssl.keyAlias"
    val hostSslKeyStorePassword = "ktor.security.ssl.keyStorePassword"
    val hostSslPrivateKeyPassword = "ktor.security.ssl.privateKeyPassword"

    val applicationId = combinedConfig.tryGetString(applicationIdPath) ?: "Application"
    val appLog = LoggerFactory.getLogger(applicationId)
    if (configFile != null && !configFile.exists()) {
        appLog.error("Configuration file '$configFile' specified as command line argument was not found")
        appLog.warn("Will attempt to start without loading configuration…")
    }

    val environment = applicationHostEnvironment {
        log = appLog
        classLoader = jar?.let { URLClassLoader(arrayOf(jar), ApplicationEnvironment::class.java.classLoader) }
                ?: ApplicationEnvironment::class.java.classLoader
        config = HoconApplicationConfig(combinedConfig)

        val contentHiddenValue = ConfigValueFactory.fromAnyRef("***", "Content hidden")
        log.trace(combinedConfig.getObject("ktor")
                .withoutKey("security")
                .withValue("security", contentHiddenValue)
                .render())


        val host = argsMap["-host"] ?: combinedConfig.tryGetString(hostConfigPath) ?: "0.0.0.0"
        val port = argsMap["-port"] ?: combinedConfig.tryGetString(hostPortPath) ?: "80"
        val sslPort = argsMap["-sslPort"] ?: combinedConfig.tryGetString(hostSslPortPath)
        val sslKeyStorePath = argsMap["-sslKeyStore"] ?: combinedConfig.tryGetString(hostSslKeyStore)
        val sslKeyStorePassword = combinedConfig.tryGetString(hostSslKeyStorePassword)?.trim()
        val sslPrivateKeyPassword = combinedConfig.tryGetString(hostSslPrivateKeyPassword)?.trim()
        val sslKeyAlias = combinedConfig.tryGetString(hostSslKeyAlias) ?: "mykey"

        connector {
            this.host = host
            this.port = port.toInt()
        }

        if (sslPort != null) {
            if (sslKeyStorePath == null) {
                throw IllegalArgumentException("SSL requires keystore: use -sslKeyStore=path or $hostSslKeyStore config")
            }
            if (sslKeyStorePassword == null) {
                throw IllegalArgumentException("SSL requires keystore password: use $hostSslKeyStorePassword config")
            }
            if (sslPrivateKeyPassword == null) {
                throw IllegalArgumentException("SSL requires certificate password: use $hostSslPrivateKeyPassword config")
            }

            val keyStoreFile = File(sslKeyStorePath).let { file ->
                if (file.exists() || file.isAbsolute)
                    file
                else
                    File(".", sslKeyStorePath).absoluteFile
            }
            val keyStore = KeyStore.getInstance("JKS").apply {
                FileInputStream(keyStoreFile).use {
                    load(it, sslKeyStorePassword.toCharArray())
                }

                requireNotNull(getKey(sslKeyAlias, sslPrivateKeyPassword.toCharArray()) == null) {
                    "The specified key $sslKeyAlias doesn't exist in the key store $sslKeyStorePath"
                }
            }

            sslConnector(keyStore, sslKeyAlias,
                    { sslKeyStorePassword.toCharArray() },
                    { sslPrivateKeyPassword.toCharArray() }) {
                this.host = host
                this.port = sslPort.toInt()
                this.keyStorePath = keyStoreFile
            }
        }

        (argsMap["-watch"]?.split(",") ?: combinedConfig.tryGetStringList(hostWatchPaths))?.let {
            watchPaths = it
        }
    }

    return environment
}

private fun String.splitPair(ch: Char): Pair<String, String>? = indexOf(ch).let { idx ->
    when (idx) {
        -1 -> null
        else -> Pair(take(idx), drop(idx + 1))
    }
}

