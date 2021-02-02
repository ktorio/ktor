/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.tests.*
import javax.net.ssl.*
import kotlin.test.*

class AndroidHttpClientTest : HttpClientTest(Android)

class AndroidSslOverProxyTest : SslOverProxyTest<AndroidEngineConfig>(Android) {

    private lateinit var defaultSslSocketFactory: SSLSocketFactory

    override fun AndroidEngineConfig.disableCertificatePinning() {
        defaultSslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(unsafeSslContext.socketFactory)
    }

    @AfterTest
    fun tearDown() {
        HttpsURLConnection.setDefaultSSLSocketFactory(defaultSslSocketFactory)
    }
}
