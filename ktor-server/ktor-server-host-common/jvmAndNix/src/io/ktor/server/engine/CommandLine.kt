/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.config.*
import io.ktor.util.*
import io.ktor.util.logging.*

internal object ConfigKeys {
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
    val developmentModeKey = "ktor.development"
}

/**
 * Creates an [ApplicationEngineEnvironment] instance from command line arguments
 */
public fun commandLineEnvironment(args: Array<String>): ApplicationEngineEnvironment {
    val argumentsPairs = args.mapNotNull { it.splitPair('=') }.toMap()
    val configuration = buildApplicationConfig(args)
    val applicationId = configuration.tryGetString(ConfigKeys.applicationIdPath) ?: "Application"
    val logger = KtorSimpleLogger(applicationId)

    val rootPath = argumentsPairs["-path"] ?: configuration.tryGetString(ConfigKeys.rootPathPath) ?: ""

    val environment = applicationEngineEnvironment {
        log = logger

        configurePlatformProperties(args)

        config = configuration

        this.rootPath = rootPath

        val host = argumentsPairs["-host"] ?: configuration.tryGetString(ConfigKeys.hostConfigPath) ?: "0.0.0.0"
        val port = argumentsPairs["-port"] ?: configuration.tryGetString(ConfigKeys.hostPortPath)
        val sslPort = argumentsPairs["-sslPort"] ?: configuration.tryGetString(ConfigKeys.hostSslPortPath)
        val sslKeyStorePath = argumentsPairs["-sslKeyStore"] ?: configuration.tryGetString(ConfigKeys.hostSslKeyStore)
        val sslKeyStorePassword = configuration.tryGetString(ConfigKeys.hostSslKeyStorePassword)?.trim()
        val sslPrivateKeyPassword = configuration.tryGetString(ConfigKeys.hostSslPrivateKeyPassword)?.trim()
        val sslKeyAlias = configuration.tryGetString(ConfigKeys.hostSslKeyAlias) ?: "mykey"

        developmentMode = configuration.tryGetString(ConfigKeys.developmentModeKey)
            ?.let { it.toBoolean() } ?: PlatformUtils.IS_DEVELOPMENT_MODE

        if (port != null) {
            connector {
                this.host = host
                this.port = port.toInt()
            }
        }

        if (sslPort != null) {
            configureSSLConnectors(
                host,
                sslPort,
                sslKeyStorePath,
                sslKeyStorePassword,
                sslPrivateKeyPassword,
                sslKeyAlias
            )
        }

        if (port == null && sslPort == null) {
            throw IllegalArgumentException(
                "Neither port nor sslPort specified. Use command line options -port/-sslPort " +
                    "or configure connectors in application.conf"
            )
        }

        (argumentsPairs["-watch"]?.split(",") ?: configuration.tryGetStringList(ConfigKeys.hostWatchPaths))?.let {
            watchPaths = it
        }
    }

    return environment
}

internal fun buildApplicationConfig(args: Array<String>): ApplicationConfig {
    val argumentsPairs = args.mapNotNull { it.splitPair('=') }
    val commandLineProperties = argumentsPairs
        .filter { it.first.startsWith("-P:") }
        .map { it.first.removePrefix("-P:") to it.second }

    val configPath = argumentsPairs.firstOrNull { it.first == "-config" }?.second

    val commandLineConfig = MapApplicationConfig(commandLineProperties)
    val environmentConfig = getConfigFromEnvironment()
    val fileConfig = configLoaders.firstNotNullOfOrNull { it.load(configPath) } ?: MapApplicationConfig()

    return listOf(commandLineConfig, environmentConfig, fileConfig).merge()
}

internal expect fun ApplicationEngineEnvironmentBuilder.configureSSLConnectors(
    host: String,
    sslPort: String,
    sslKeyStorePath: String?,
    sslKeyStorePassword: String?,
    sslPrivateKeyPassword: String?,
    sslKeyAlias: String
)

internal expect fun ApplicationEngineEnvironmentBuilder.configurePlatformProperties(args: Array<String>)

internal expect fun getConfigFromEnvironment(): ApplicationConfig

/**
 * Load engine's configuration suitable for all engines from [deploymentConfig]
 */
public fun BaseApplicationEngine.Configuration.loadCommonConfiguration(deploymentConfig: ApplicationConfig) {
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

internal fun String.splitPair(ch: Char): Pair<String, String>? = indexOf(ch).let { idx ->
    when (idx) {
        -1 -> null
        else -> Pair(take(idx), drop(idx + 1))
    }
}
