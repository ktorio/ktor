package org.jetbrains.ktor.host

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import java.io.*
import java.net.*
import java.security.*

fun commandLineConfig(args: Array<String>): Pair<ApplicationHostConfig, ApplicationEnvironment> {
    val argsMap = args.mapNotNull { it.splitPair('=') }.toMap()

    val jar = argsMap["-jar"]?.let { File(it).toURI().toURL() }
    val argConfig = ConfigFactory.parseMap(argsMap.filterKeys { it.startsWith("-P:") }.mapKeys { it.key.removePrefix("-P:") }, "Command-line options")
    val combinedConfig = argConfig.withFallback(ConfigFactory.load())

    val applicationIdPath = "ktor.application.id"

    val hostConfigPath = "ktor.deployment.host"
    val hostPortPath = "ktor.deployment.port"
    val hostReload = "ktor.deployment.autoreload"

    val hostSslPortPath = "ktor.deployment.sslPort"
    val hostSslKeyStore = "ktor.security.ssl.keyStore"
    val hostSslKeyAlias = "ktor.security.ssl.keyAlias"
    val hostSslKeyStorePassword = "ktor.security.ssl.keyStorePassword"
    val hostSslPrivateKeyPassword = "ktor.security.ssl.privateKeyPassword"

    val applicationId = combinedConfig.tryGetString(applicationIdPath) ?: "Application"
    val log = SLF4JApplicationLog(applicationId)
    val classLoader = jar?.let { URLClassLoader(arrayOf(jar), ApplicationEnvironment::class.java.classLoader) }
            ?: ApplicationEnvironment::class.java.classLoader
    val appConfig = HoconApplicationConfig(combinedConfig)

    val contentHiddenValue = ConfigValueFactory.fromAnyRef("***", "Content hidden")
    log.trace(combinedConfig.getObject("ktor")
            .withoutKey("security")
            .withValue("security", contentHiddenValue)
            .render())

    val hostConfig = applicationHostConfig {
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

            val keyStoreFile = File(sslKeyStorePath).let { file -> if (file.exists() || file.isAbsolute) file else File(".", sslKeyStorePath).absoluteFile }
            val keyStore = KeyStore.getInstance("JKS").apply {
                FileInputStream(keyStoreFile).use {
                    load(it, sslKeyStorePassword.toCharArray())
                }

                requireNotNull(getKey(sslKeyAlias, sslPrivateKeyPassword.toCharArray()) == null) { "The specified key $sslKeyAlias doesn't exist in the key store $sslKeyStorePath" }
            }

            sslConnector(keyStore, sslKeyAlias, { sslKeyStorePassword.toCharArray() }, { sslPrivateKeyPassword.toCharArray() }) {
                this.host = host
                this.port = sslPort.toInt()
                this.keyStorePath = keyStoreFile
            }
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

