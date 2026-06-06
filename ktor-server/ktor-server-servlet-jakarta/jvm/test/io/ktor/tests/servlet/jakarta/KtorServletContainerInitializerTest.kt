/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.servlet.jakarta

import io.ktor.server.servlet.jakarta.KtorServletContainerInitializer
import io.ktor.server.servlet.jakarta.KtorServletContextListener
import io.ktor.server.servlet.jakarta.ManagedServerKey
import io.ktor.server.servlet.jakarta.ServletApplicationEngine
import io.ktor.server.servlet.jakarta.ServletApplicationEngine.Companion.ApplicationAttributeKey
import io.ktor.utils.io.InternalAPI
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletRegistration
import kotlin.test.Test

@OptIn(InternalAPI::class)
class KtorServletContainerInitializerTest {

    @Test
    fun registersLifecycleListenerWhenNotEmbedded() {
        val ctx = mockk<ServletContext>(relaxed = true) {
            every { getAttribute(ApplicationAttributeKey) } returns null
        }

        KtorServletContainerInitializer().onStartup(null, ctx)

        verify { ctx.addListener(KtorServletContextListener::class.java) }
    }

    @Test
    fun doesNotRegisterListenerInEmbeddedMode() {
        val ctx = mockk<ServletContext>(relaxed = true) {
            every { getAttribute(ApplicationAttributeKey) } returns Any()
        }

        KtorServletContainerInitializer().onStartup(null, ctx)

        verify(exactly = 0) { ctx.addListener(KtorServletContextListener::class.java) }
    }

    @Test
    fun skipsContextManagementWhenMultipleEngineRegistrations() {
        val registration = mockk<ServletRegistration> {
            every { className } returns ServletApplicationEngine::class.java.name
        }
        val ctx = mockk<ServletContext>(relaxed = true) {
            every { getAttribute(ApplicationAttributeKey) } returns null
            every { getAttribute(ManagedServerKey) } returns null
            every { classLoader } returns this@KtorServletContainerInitializerTest::class.java.classLoader
            every { servletRegistrations } returns mapOf("first" to registration, "second" to registration)
        }
        val event = mockk<ServletContextEvent> { every { servletContext } returns ctx }

        KtorServletContextListener().contextInitialized(event)

        // Falls back to per-servlet self-bootstrap: warns and does not take over the lifecycle.
        verify { ctx.log(any()) }
        verify(exactly = 0) { ctx.setAttribute(ManagedServerKey, any()) }
    }
}
