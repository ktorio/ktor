/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import android.content.Context
import android.content.res.AssetManager
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.cookies.publicsuffix.*
import io.mockk.*
import java.io.File
import java.io.IOException
import kotlin.test.*

class BundledPublicSuffixRulesAndroidTest {

    @Test
    fun testLoadsFromApplicationContextAssets() {
        val context = mockk<Context>()
        val applicationContext = mockk<Context>()
        val assets = mockk<AssetManager>()
        every { context.applicationContext } returns applicationContext
        every { applicationContext.assets } returns assets
        every { assets.open(PUBLIC_SUFFIX_RESOURCE) } answers { bundledResource().inputStream() }

        val rules = PublicSuffixRules.bundled(context)
        verify(exactly = 1) { context.applicationContext }
        verify(exactly = 0) { assets.open(any()) }

        assertBundledPublicSuffixVectors(rules)
        verify(exactly = 1) { assets.open(PUBLIC_SUFFIX_RESOURCE) }
    }

    @Test
    fun testLoadBundledReadsApplicationContextAssetsImmediately() {
        val context = mockk<Context>()
        val applicationContext = mockk<Context>()
        val assets = mockk<AssetManager>()
        every { context.applicationContext } returns applicationContext
        every { applicationContext.assets } returns assets
        every { assets.open(PUBLIC_SUFFIX_RESOURCE) } answers { bundledResource().inputStream() }

        val rules = PublicSuffixRules.loadBundled(context)
        verify(exactly = 1) { assets.open(PUBLIC_SUFFIX_RESOURCE) }

        assertBundledPublicSuffixVectors(rules)
        verify(exactly = 1) { assets.open(PUBLIC_SUFFIX_RESOURCE) }
    }

    @Test
    fun testLoadBundledFailsImmediately() {
        assertFailsWith<IOException> {
            PublicSuffixRules.loadBundled(contextWithAssets { throw IOException("missing") })
        }
        assertFailsWith<IllegalStateException> {
            PublicSuffixRules.loadBundled(contextWithAssets { byteArrayOf(0, 0, 0, 1).inputStream() })
        }
    }

    @Test
    fun testMissingAssetFails() {
        val rules = PublicSuffixRules.bundled(contextWithAssets { throw IOException("missing") })

        repeat(2) {
            assertFailsWith<IOException> { rules.isPublicSuffix("com") }
        }
    }

    @Test
    fun testMalformedAssetFails() {
        val rules = PublicSuffixRules.bundled(contextWithAssets { byteArrayOf(0, 0, 0, 1).inputStream() })

        repeat(2) {
            assertFailsWith<IllegalStateException> { rules.isPublicSuffix("com") }
        }
    }

    private fun contextWithAssets(open: () -> java.io.InputStream): Context {
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        every { context.applicationContext } returns context
        every { context.assets } returns assets
        every { assets.open(PUBLIC_SUFFIX_RESOURCE) } answers { open() }
        return context
    }

    private fun bundledResource(): ByteArray =
        File("public-suffix-list/assets/$PUBLIC_SUFFIX_RESOURCE").readBytes()
}
