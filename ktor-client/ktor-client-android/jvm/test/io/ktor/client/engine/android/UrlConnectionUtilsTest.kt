/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class UrlConnectionUtilsTest {

    private val data = HttpRequestBuilder().build()

    @Test
    fun `timeout-aware connection catches error in connect`(): Unit = runTest {
        val connection = TestConnection(throwInConnect = true)
        assertFailsWith<Throwable>("Connect timeout has expired") {
            connection.timeoutAwareConnection(data) {
                it.connect()
            }
        }
    }

    @Test
    fun `timeout-aware connection catches error in response status code`(): Unit = runTest {
        val connection = TestConnection(throwInResponseCode = true)
        assertFailsWith<Throwable>("Connect timeout has expired") {
            connection.timeoutAwareConnection(data) {
                it.responseCode
            }
        }
    }

    @Test
    fun `disconnects immediately on cancellation`(): Unit = runTest {
        val job = Job(coroutineContext.job)
        val connection = TestConnection()
        connection.disconnectOnCancellation(job)

        // Keep the job cancelling while asserting that disconnect runs before child completion.
        CoroutineScope(coroutineContext + job).launch(start = CoroutineStart.UNDISPATCHED) {
            awaitCancellation()
        }

        job.cancel()

        assertFalse(job.isCompleted)
        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `does not disconnect on completion`() {
        val job = Job()
        val connection = TestConnection()
        connection.disconnectOnCancellation(job)

        job.complete()

        assertEquals(0, connection.disconnectCount)
    }
}

private class TestConnection(
    private val throwInConnect: Boolean = false,
    private val throwInResponseCode: Boolean = false,
) : HttpURLConnection(URL("https://example.com")) {

    var disconnectCount: Int = 0
        private set

    override fun getResponseCode(): Int {
        if (throwInResponseCode) throw ConnectException("Connect timed out")
        return 200
    }

    override fun connect() {
        if (throwInConnect) throw SocketTimeoutException()
    }

    override fun disconnect() {
        disconnectCount++
    }

    override fun usingProxy(): Boolean {
        throw NotImplementedError()
    }
}
