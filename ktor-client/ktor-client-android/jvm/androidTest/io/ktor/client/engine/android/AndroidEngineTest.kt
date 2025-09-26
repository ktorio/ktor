/*
* Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.android

import android.net.http.ConnectionMigrationOptions
import android.net.http.HttpEngine
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains


class AndroidEngineTest {
    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun testHttpEngine() = runTest {
        val client = HttpClient(Android.create {
            httpEngineDisabled = false
            context = InstrumentationRegistry.getInstrumentation().targetContext
        })
        try {
            val response = client.get("https://github.com/robots.txt")
            assertContains(response.bodyAsText(), "Disallow")
        } finally {
            client.close()
        }
    }
    @Test
    fun testHttpEngineConfig() = runTest {
        val client = HttpClient(Android.create {
            httpEngineDisabled = false
            context = InstrumentationRegistry.getInstrumentation().targetContext

            httpEngineConfig = {
                setUserAgent("Xyz")

                setEnableBrotli(true)
                setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_IN_MEMORY, 10_000_000)
                setConnectionMigrationOptions(
                    ConnectionMigrationOptions.Builder()
                        .setDefaultNetworkMigration(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                        .setPathDegradationMigration(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                        .setAllowNonDefaultNetworkUsage(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                        .build(),
                )
                addQuicHint("github.com", 443, 443)
            }
        })

        try {
            val response = client.get("https://github.com/robots.txt")

            assertContains(response.bodyAsText(), "Disallow")
        } finally {
            client.close()
        }
    }

    @Test
    fun testUrlConnection() = runTest {
        val client = HttpClient(Android.create {
            httpEngineDisabled = true
        })

        try {
            val response = client.get("https://github.com/robots.txt")

            println(response.bodyAsText())
            assertContains(response.bodyAsText(), "Disallow")
        } finally {
            client.close()
        }
    }
}
