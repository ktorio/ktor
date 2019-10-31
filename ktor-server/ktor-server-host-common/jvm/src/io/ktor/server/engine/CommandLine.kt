/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import com.typesafe.config.*
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import java.io.*
import java.net.*
import java.security.*

/**
 * Creates an [ApplicationEngineEnvironment] instance from command line arguments
 */
fun commandLineEnvironment(args: Array<String>): ApplicationEngineEnvironment {
    val argsMap = args.mapNotNull { it.splitPair('=') }.toMap()

    val jar = argsMap["-jar"]?.let {
        when {
            it.startsWith("file:") || it.startsWith("jrt:") || it.startsWith("jar:") -> URI(it).toURL()
            else -> File(it).toURI().toURL()
        }
    }
    val configFile = argsMap["-config"]?.let { File(it) }
    val commandLineMap = argsMap.filterKeys { it.startsWith("-P:") }.mapKeys { it.key.removePrefix("-P:") }

    val environmentConfig = ConfigFactory.systemProperties().withOnlyPath("ktor")
    val fileConfig = configFile?.let { ConfigFactory.parseFile(it) } ?: ConfigFactory.load()
    val argConfig = ConfigFactory.parseMap(commandLineMap, "Command-line options")
    val combinedConfig = argConfig.withFallback(fileConfig).withFallback(environmentConfig).resolve()

    val applicationIdPath = "ktor.application.id"

    val hostConfigPath = "ktor.deployment.host"
    val hostPortPath = "ktor.deployment.port"
    val hostWatchPaths = "ktor.deployment.watch"

    val rootPathPath = "ktor.deployment.rootPath"

    val hostSslPortPath = "ktor.deployment.sslPort"
    val hostSslKeyStore = "ktor.security.ssl.keyStore"
    val hostSslKeyAlias = "ktor.security.ssl.keyAlias"
    val hostSslKeyStorePassword = "ktor.security.ssl.keyStorePassword"
    val hostSslPrivateKeyPassword = "ktor.security.ssl.privateKeyPassword"

    val applicationId = combinedConfig.tryGetString(applicationIdPath) ?: "Application"
    val appLog = logger().addName(applicationId)
    if (configFile != null && !configFile.exists()) {
        appLog.error("Configuration file '$configFile' specified as command line argument was not found")
        appLog.warning("Will attempt to start without loading configuration…")
    }
    val rootPath = argsMap["-path"] ?: combinedConfig.tryGetString(rootPathPath) ?: ""

    val environment = applicationEngineEnvironment {
        log = appLog
        classLoader = jar?.let { URLClassLoader(arrayOf(jar), ApplicationEnvironment::class.java.classLoader) }
            ?: ApplicationEnvironment::class.java.classLoader
        config = HoconApplicationConfig(combinedConfig)
        this.rootPath = rootPath

        val contentHiddenValue = ConfigValueFactory.fromAnyRef("***", "Content hidden")
        if (combinedConfig.hasPath("ktor")) {
            log.trace(
                combinedConfig.getObject("ktor")
                    .withoutKey("security")
                    .withValue("security", contentHiddenValue)
                    .render()
            )
        } else {
            log.trace("No configuration provided: neither application.conf " +
                "nor system properties nor command line options (-config or -P:ktor...=) provided")
        }

        val host = argsMap["-host"] ?: combinedConfig.tryGetString(hostConfigPath) ?: "0.0.0.0"
        val port = argsMap["-port"] ?: combinedConfig.tryGetString(hostPortPath)
        val sslPort = argsMap["-sslPort"] ?: combinedConfig.tryGetString(hostSslPortPath)
        val sslKeyStorePath = argsMap["-sslKeyStore"] ?: combinedConfig.tryGetString(hostSslKeyStore)
        val sslKeyStorePassword = combinedConfig.tryGetString(hostSslKeyStorePassword)?.trim()
        val sslPrivateKeyPassword = combinedConfig.tryGetString(hostSslPrivateKeyPassword)?.trim()
        val sslKeyAlias = combinedConfig.tryGetString(hostSslKeyAlias) ?: "mykey"

        if (port != null) {
            connector {
                this.host = host
                this.port = port.toInt()
            }
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

        if (port == null && sslPort == null) {
            throw IllegalArgumentException(
                "Neither port nor sslPort specified. Use command line options -port/-sslPort " +
                    "or configure connectors in application.conf"
            )
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

/**
 * Load engine's configuration suitable for all engines from [deploymentConfig]
 */
fun BaseApplicationEngine.Configuration.loadCommonConfiguration(deploymentConfig: ApplicationConfig) {
    deploymentConfig.propertyOrNull("callGroupSize")?.getString()?.toInt()?.let {
        callGroupSize = it
    }
    deploymentConfig.propertyOrNull("connectionGroupSize")?.getString()?.toInt()?.let {
        connectionGroupSize = it
    }
    deploymentConfig.propertyOrNull("workerGroupSize")?.getString()?.toInt()?.let {
        workerGroupSize = it
    }
}
