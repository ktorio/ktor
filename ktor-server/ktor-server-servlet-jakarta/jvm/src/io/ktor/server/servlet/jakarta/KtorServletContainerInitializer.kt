/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.server.application.*
import io.ktor.server.servlet.jakarta.ServletApplicationEngine.Companion.ApplicationAttributeKey
import io.ktor.server.servlet.jakarta.ServletApplicationEngine.Companion.ApplicationEnginePipelineAttributeKey
import io.ktor.utils.io.InternalAPI
import jakarta.servlet.ServletContainerInitializer
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener

/**
 * Registers [KtorServletContextListener] so a servlet-hosted Ktor application is started and stopped in
 * lockstep with the web application context lifecycle.
 *
 * Registered automatically via `META-INF/services/jakarta.servlet.ServletContainerInitializer`, so no
 * user configuration is required. It is a no-op when the application is driven by an embedded engine,
 * which injects [ServletApplicationEngine.ApplicationAttributeKey] and owns the lifecycle itself.
 *
 * It must be `public` so the servlet container can instantiate it via `ServiceLoader`, but it is
 * internal infrastructure and not intended to be referenced directly.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.KtorServletContainerInitializer)
 */
@InternalAPI
public class KtorServletContainerInitializer : ServletContainerInitializer {
    override fun onStartup(classes: MutableSet<Class<*>>?, ctx: ServletContext) {
        // Embedded engine mode owns the lifecycle; do not register the listener.
        if (ctx.getAttribute(ApplicationAttributeKey) != null) return
        ctx.addListener(KtorServletContextListener::class.java)
    }
}

/**
 * Starts and stops a servlet-hosted Ktor application together with the web application context, so the
 * lifecycle events fire at deploy/undeploy regardless of `load-on-startup` (see KTOR-8524).
 *
 * Registered automatically by [KtorServletContainerInitializer]; it can also be added as a `<listener>`
 * in `web.xml`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.KtorServletContextListener)
 */
public class KtorServletContextListener : ServletContextListener {
    override fun contextInitialized(sce: ServletContextEvent) {
        val ctx = sce.servletContext
        // Embedded engine mode owns the lifecycle.
        if (ctx.getAttribute(ApplicationAttributeKey) != null) return
        // Defensive: never start the application twice.
        if (ctx.managedEmbeddedServer() != null) return

        val registrations = ctx.servletRegistrations?.values?.filter { registration ->
            val className = registration.className ?: return@filter false
            val servletClass = runCatching { ctx.classLoader.loadClass(className) }.getOrNull()
            servletClass != null && ServletApplicationEngine::class.java.isAssignableFrom(servletClass)
        }.orEmpty()

        // Context-managed lifecycle assumes a single Ktor application per web application context.
        // With more than one ServletApplicationEngine, fall back to per-servlet self-bootstrap so each
        // servlet keeps its own application (matching the pre-listener behavior) instead of sharing one.
        val registration = registrations.singleOrNull() ?: run {
            if (registrations.size > 1) {
                ctx.log(
                    "Multiple ServletApplicationEngine registrations found; context-managed application " +
                        "lifecycle is disabled. Each servlet bootstraps its own application on init()."
                )
            }
            return
        }

        val initParameters = registration.initParameters.toList() + ctx.contextInitParameters()

        val bootstrap = bootstrapServletApplication(ctx, initParameters)
        bootstrap.server.start()

        ctx.setAttribute(ManagedServerKey, bootstrap.server)
        ctx.setAttribute(ApplicationEnginePipelineAttributeKey, bootstrap.enginePipeline)
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        val ctx = sce.servletContext
        val server = ctx.managedEmbeddedServer() ?: return

        server.application.monitor.raise(ApplicationStopPreparing, server.environment)
        server.stop()
        ctx.removeAttribute(ManagedServerKey)
    }
}

private fun ServletContext.contextInitParameters(): List<Pair<String, String>> =
    initParameterNames?.toList().orEmpty().mapNotNull { name ->
        getInitParameter(name)?.let { name to it }
    }
