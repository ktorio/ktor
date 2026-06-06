/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.engine.*
import io.ktor.server.servlet.jakarta.ServletApplicationEngine.Companion.ApplicationAttributeKey
import io.ktor.util.*
import jakarta.servlet.*
import jakarta.servlet.annotation.*
import org.slf4j.*
import kotlin.coroutines.*

/**
 * This servlet need to be installed into a servlet container
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.ServletApplicationEngine)
 */
@MultipartConfig
public open class ServletApplicationEngine : KtorServlet() {

    override val managedByEngineHeaders: Set<String>
        get() = if (servletContext.isTomcat()) {
            setOf(HttpHeaders.TransferEncoding, HttpHeaders.Connection)
        } else {
            emptySet()
        }

    private val bootstrap: ServletApplicationBootstrap? by lazy {
        // External injection mode: the embedded engine (Jetty/Tomcat) owns the application lifecycle.
        if (servletContext.getAttribute(ApplicationAttributeKey) != null) return@lazy null

        // Managed mode: KtorServletContainerInitializer already created and started the server at
        // deployment time. Reuse that instance instead of bootstrapping (and starting) a second one.
        servletContext.managedEmbeddedServer()?.let { server ->
            return@lazy ServletApplicationBootstrap(
                server,
                servletContext.getAttribute(ApplicationEnginePipelineAttributeKey) as EnginePipeline
            )
        }

        // Fallback (no ServletContainerInitializer ran): self-bootstrap from servlet init parameters.
        bootstrapServletApplication(servletContext, collectInitParameters())
    }

    private val embeddedServer: ServletEmbeddedServer?
        get() = bootstrap?.server

    private fun collectInitParameters(): List<Pair<String, String>> {
        val names = (
            servletContext.initParameterNames?.toList().orEmpty() +
                servletConfig.initParameterNames?.toList().orEmpty()
            ).distinct()
        return names.mapNotNull { name ->
            (servletConfig.getInitParameter(name) ?: servletContext.getInitParameter(name))?.let { name to it }
        }
    }

    public val environment: ApplicationEnvironment
        get() = servletContext.getAttribute(EnvironmentAttributeKey)?.let { it as ApplicationEnvironment }
            ?: embeddedServer!!.environment

    @Suppress("UNCHECKED_CAST")
    override val application: Application
        get() = servletContext.getAttribute(ApplicationAttributeKey)?.let { it as () -> Application }?.invoke()
            ?: embeddedServer!!.application

    override val logger: Logger get() = environment.log

    override val enginePipeline: EnginePipeline by lazy {
        servletContext.getAttribute(ApplicationEnginePipelineAttributeKey)?.let { return@lazy it as EnginePipeline }

        bootstrap!!.enginePipeline
    }

    override val upgrade: ServletUpgrade by lazy {
        if ("jetty" in (servletContext.serverInfo?.toLowerCasePreservingASCIIRules() ?: "")) {
            jettyUpgrade ?: DefaultServletUpgrade
        } else {
            DefaultServletUpgrade
        }
    }

    override val coroutineContext: CoroutineContext
        get() = super.coroutineContext + application.parentCoroutineContext

    /**
     * Called by the servlet container when loading the servlet (on load)
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.ServletApplicationEngine.init)
     */
    override fun init() {
        // In managed mode the server is already started by KtorServletContainerInitializer at
        // deployment time, so it must not be started again (a second start would recreate the app).
        if (servletContext.managedEmbeddedServer() == null) {
            embeddedServer?.start()
        }
        super.init()
    }

    override fun destroy() {
        // In managed mode the server is stopped by KtorServletContextListener on context destruction,
        // which also guarantees the stop events fire even if no request was ever served.
        if (servletContext.managedEmbeddedServer() == null) {
            application.monitor.raise(ApplicationStopPreparing, environment)
            super.destroy()
            embeddedServer?.stop()
        } else {
            super.destroy()
        }
    }

    public companion object {
        /**
         * An embedded server instance key. It is not recommended to use unless you are writing
         * your own servlet application engine implementation
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.ServletApplicationEngine.Companion.EnvironmentAttributeKey)
         */
        public const val EnvironmentAttributeKey: String = "_ktor_environment_instance"
        public const val ApplicationAttributeKey: String = "_ktor_application_instance"

        /**
         * An application engine pipeline instance key. It is not recommended to use unless you are writing
         * your own servlet application engine implementation
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.servlet.jakarta.ServletApplicationEngine.Companion.ApplicationEnginePipelineAttributeKey)
         */
        public const val ApplicationEnginePipelineAttributeKey: String = "_ktor_application_engine_pipeline_instance"

        private val jettyUpgrade by lazy {
            try {
                Class.forName("io.ktor.server.jetty.jakarta.internal.JettyUpgradeImpl").kotlin.objectInstance
                    as ServletUpgrade
            } catch (t: Throwable) {
                null
            }
        }
    }
}

private object EmptyEngineFactory : ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration> {
    override fun configuration(
        configure: ApplicationEngine.Configuration.() -> Unit
    ): ApplicationEngine.Configuration {
        return ApplicationEngine.Configuration()
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): ApplicationEngine {
        return object : ApplicationEngine {
            override val environment: ApplicationEnvironment = environment
            override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
            override fun start(wait: Boolean): ApplicationEngine = this
            override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) = Unit
        }
    }
}

internal fun ServletContext.isTomcat() =
    getAttribute(ApplicationAttributeKey) == null && serverInfo.contains("tomcat", ignoreCase = true)

/**
 * Internal context attribute holding the listener-managed [EmbeddedServer].
 *
 * Kept separate from [ServletApplicationEngine.ApplicationAttributeKey] so it does not flip
 * [ServletContext.isTomcat] detection.
 */
internal const val ManagedServerKey: String = "_ktor_managed_embedded_server"

internal typealias ServletEmbeddedServer = EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>

@Suppress("UNCHECKED_CAST")
internal fun ServletContext.managedEmbeddedServer(): ServletEmbeddedServer? =
    getAttribute(ManagedServerKey) as? ServletEmbeddedServer

internal class ServletApplicationBootstrap(
    val server: ServletEmbeddedServer,
    val enginePipeline: EnginePipeline
)

/**
 * Builds (but does not start) an [EmbeddedServer] for a servlet-hosted Ktor application together with
 * its [EnginePipeline], wiring the pipeline into the application on [ApplicationStarting].
 *
 * Shared by [ServletApplicationEngine] (the self-bootstrap fallback) and [KtorServletContainerInitializer]
 * (the WAR deployment path) so the bootstrap logic lives in a single place.
 *
 * @param initParameters servlet/context init parameters as raw `name to value` pairs; only those
 * prefixed with `io.ktor.` are considered, with the prefix stripped to form the configuration keys.
 */
internal fun bootstrapServletApplication(
    servletContext: ServletContext,
    initParameters: List<Pair<String, String>>
): ServletApplicationBootstrap {
    val parameters = initParameters
        .filter { (name, _) -> name.startsWith("io.ktor.") }
        .map { (name, value) -> name.removePrefix("io.ktor.") to value }

    val parametersConfig = MapApplicationConfig(parameters)
    val combinedConfig = parametersConfig
        .withFallback(load(parametersConfig.tryGetString("ktor.config")))

    val applicationId = combinedConfig.tryGetString("ktor.application.id") ?: "Application"

    val environment = applicationEnvironment {
        config = combinedConfig
        log = LoggerFactory.getLogger(applicationId)
        classLoader = servletContext.classLoader
    }
    val applicationProperties = serverConfig(environment) {
        rootPath = servletContext.contextPath ?: "/"
    }
    val server = EmbeddedServer(applicationProperties, EmptyEngineFactory)

    val enginePipeline = defaultEnginePipeline(environment.config, server.application.developmentMode).also {
        BaseApplicationResponse.setupSendPipeline(it.sendPipeline)
    }

    server.monitor.subscribe(ApplicationStarting) {
        it.receivePipeline.merge(enginePipeline.receivePipeline)
        it.sendPipeline.merge(enginePipeline.sendPipeline)
        it.receivePipeline.installDefaultTransformations()
        it.sendPipeline.installDefaultTransformations()
    }

    return ServletApplicationBootstrap(server, enginePipeline)
}
