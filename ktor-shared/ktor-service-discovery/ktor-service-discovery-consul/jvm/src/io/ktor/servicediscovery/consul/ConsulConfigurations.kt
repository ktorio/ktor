package io.ktor.servicediscovery.consul

import io.ktor.utils.io.KtorDsl

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

/**
 * Controls how the current application registers itself with Consul.
 *
 * This includes service identity, metadata, etc.
 *
 * Usage:
 * ```
 * registration {
 *     serviceName = "ktor-api"
 *     instanceId = "ktor-api-001"
 *     metadata = mapOf("version" to "1.0.0")
 *
 *     healthCheck {
 *         path = "/health"
 *         interval = "10s"
 *     }
 * }
 * ```
 */
@KtorDsl
public class ConsulRegistrationConfig {
    public var serviceName: String = "ktor-server"
    public var instanceId: String = "ktor-server-instance"
    public var address: String = "localhost"
    public var port: Int = 8080
    public var tags: MutableList<String> = mutableListOf()
    public var metadata: MutableMap<String, String> = mutableMapOf()

    internal val healthChecks: MutableList<ConsulHealthCheckConfig> = mutableListOf()

    public fun healthCheck(block: ConsulHealthCheckConfig.() -> Unit) {
        healthChecks.add(ConsulHealthCheckConfig().apply(block))
    }
}

/**
 * Defines the health check associated with the registered service.
 *
 * These values control how often the service is checked, what endpoint is used,
 * and how long Consul waits before deregistering a failing instance.
 *
 * Usage:
 * ```
 * healthCheck {
 *     path = "/ready"
 *     interval = "5s"
 *     timeout = "2s"
 *     deregisterAfter = "1m"
 * }
 * ```
 */
@KtorDsl
public class ConsulHealthCheckConfig {
    public var path: String = "/health"
    public var interval: String = "10s"
    public var timeout: String = "5s"
    public var deregisterAfter: String = "30m"
    public var tlsSkipVerify: Boolean = false
}

/**
 * Configures how the plugin connects to the Consul agent.
 *
 * Includes basic host, port, ACL token, etc.
 *
 * Usage:
 * ```
 * connection {
 *     host = "localhost"
 *     port = 8500
 *     aclToken = "secret"
 * }
 * ```
 */
@KtorDsl
public class ConsulConnectionConfig {
    public var host: String = "localhost"
    public var port: Int = 8500
    public var path: String = ""
    public var scheme: String = "http"

    public var aclToken: String? = null

    public var tls: ConsulTlsConfig? = null

    public fun tls(block: ConsulTlsConfig.() -> Unit) {
        tls = ConsulTlsConfig().apply(block)
    }
}

/**
 * TLS configuration for secure Consul communication.
 *
 * This includes CA certificates, client certificate/key files, and hostname verification.
 *
 * Usage:
 * ```
 * tls {
 *     keyStorePath = "/etc/ssl/client-key.pem"
 * }
 * ```
 */
@KtorDsl
public class ConsulTlsConfig {
    public var keyStoreInstanceType: KeyStoreInstanceType? = null
    public var certificatePath: String? = null
    public var certificatePassword: String? = null
    public var keyStorePath: String? = null
    public var keyStorePassword: String? = null
}

public enum class KeyStoreInstanceType {
    JKS, JCEKS, PKCS12, PKCS11, DKS
}

/**
 * Configures how this application performs service lookups (discovery).
 *
 * Includes tag filtering, load-balancing preferences, and healthy-only resolution.
 *
 * Usage:
 * ```
 * discovery {
 *     queryPassingOnly = true
 *     datacenter = "europe-west-1"
 * }
 * ```
 */
@KtorDsl
public class ConsulDiscoveryConfig {
    public var datacenter: String? = null
    public var queryPassingOnly: Boolean = true
    public var tags: MutableList<String> = mutableListOf()
}
