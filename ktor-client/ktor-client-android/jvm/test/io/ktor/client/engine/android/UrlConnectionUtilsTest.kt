/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.request.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.test.*

class UrlConnectionUtilsTest {

    private val data = HttpRequestBuilder().build()

    @Test
    fun testTimeoutAwareConnectionCatchesErrorInConnect(): Unit = runBlocking {
        val connection = TestConnection(true, false)
        assertFailsWith<Throwable>("Connect timeout has expired") {
            connection.timeoutAwareConnection(data) {
                it.connect()
            }
        }
    }

    @Test
    fun testTimeoutAwareConnectionCatchesErrorInResponseStatusCode(): Unit = runBlocking {
        val connection = TestConnection(false, true)
        assertFailsWith<Throwable>("Connect timeout has expired") {
            connection.timeoutAwareConnection(data) {
                it.responseCode
            }
        }
    }
}

private class TestConnection(
    private val throwInConnect: Boolean,
    private val throwInResponseCode: Boolean,
) : HttpURLConnection(URL("https://example.com")) {

    override fun getResponseCode(): Int {
        if (throwInResponseCode) throw ConnectException("Connect timed out")
        return 200
    }

    override fun connect() {
        if (throwInConnect) throw SocketTimeoutException()
    }

    override fun disconnect() {
        throw NotImplementedError()
    }

    override fun usingProxy(): Boolean {
        throw NotImplementedError()
    }
}
