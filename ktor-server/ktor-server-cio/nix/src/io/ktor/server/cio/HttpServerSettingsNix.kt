/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.network.tls.*
import io.ktor.server.engine.*

internal actual fun TLSConfigBuilder.takeFromConnector(connectorConfig: EngineSSLConnectorConfig) {
    connectorConfig.authentication?.let { config ->
        config.pkcs12Certificate?.let { certificate ->
            authentication(config.privateKeyPassword) {
                pkcs12Certificate(certificate.path, certificate.passwordProvider)
            }
        }
    }
    connectorConfig.verification?.let { config ->
        config.pkcs12Certificate?.let { certificate ->
            verification {
                pkcs12Certificate(certificate.path, certificate.passwordProvider)
            }
        }
    }
}
