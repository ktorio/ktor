/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.servicediscovery.consul

import com.ecwid.consul.transport.TLSConfig
import com.ecwid.consul.v1.ConsulClient
import com.ecwid.consul.v1.ConsulRawClient
import io.ktor.utils.io.InternalAPI

@InternalAPI
public fun createConsulClient(connection: ConsulConnectionConfig): ConsulClient {
    val tls = connection.tls

    val scheme = connection.scheme
    val host = connection.host
    val port = connection.port
    val path = connection.path.trim('/')

    val builder = ConsulRawClient.Builder.builder()

    builder.setHost("$scheme://$host")
    builder.setPort(port)
    if (path.isNotEmpty()) {
        builder.setPath(path)
    }

    if (tls != null) {
        val tlsConfig = TLSConfig(
            tls.keyStoreInstanceType?.let { TLSConfig.KeyStoreInstanceType.valueOf(it.name) },
            tls.certificatePath,
            tls.certificatePassword,
            tls.keyStorePath,
            tls.keyStorePassword
        )
        builder.setTlsConfig(tlsConfig)
    }

    return ConsulClient(builder.build())
}
