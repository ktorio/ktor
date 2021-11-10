/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import com.typesafe.config.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.server.testing.client.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*

public interface ClientProvider {
    public val client: HttpClient
    public fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient
}

public class TestApplication internal constructor(
    private val externalServices: Map<String, TestApplication>
) : ClientProvider {

    internal val config = HoconApplicationConfig(ConfigFactory.load())
    internal val environment = createTestEnvironment {
        config = this@TestApplication.config
    }
    private val engine = TestApplicationEngine(environment = this@TestApplication.environment)
    internal val application = engine.application

    public override val client by lazy { createClient { } }

    public override fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient {
        return HttpClient(DelegatingTestClientEngine) {
            engine {
                mainEngineHostWithPort = runBlocking {
                    engine.resolvedConnectors().first().let { "${it.host}:${it.port}" }
                }
                mainEngine = TestHttpClientEngine(TestHttpClientConfig().apply { app = engine })
                externalServices.forEach { (authority, testApplication) ->
                    externalEngines[authority] = TestHttpClientEngine(
                        TestHttpClientConfig().apply { app = testApplication.engine }
                    )
                }
            }
            block()
        }
    }

    public fun start() {
        engine.start()
    }

    public fun stop() {
        engine.stop(0, 0)
        externalServices.values.forEach { it.stop() }
    }
}

public fun TestApplication(
    externalServices: ExternalServicesBuilder.() -> Unit = {},
    block: ApplicationTestBuilder.() -> Unit
): TestApplication {
    val externalServicesBuilder = ExternalServicesBuilder().apply(externalServices)
    val testApplication = TestApplication(externalServicesBuilder.externalApplication)
    val builder = ApplicationTestBuilder(testApplication)
    builder.block()
    return testApplication
}

public class ExternalServicesBuilder {
    internal val externalApplication = mutableMapOf<String, TestApplication>()

    public fun hosts(vararg hosts: String, block: Application.() -> Unit) {
        val testApplication = TestApplication {
            application.block()
        }
        hosts.forEach {
            val protocolWithAuthority = Url(it).protocolWithAuthority
            externalApplication[protocolWithAuthority] = testApplication
        }
    }
}

public open class ApplicationTestBuilder(testApplication: TestApplication) {
    public val config: ApplicationConfig = testApplication.config
    public val environment: ApplicationEnvironment = testApplication.environment
    public val application: Application = testApplication.application
}

public class WithApplicationTestBuilder(private val testApplication: TestApplication) :
    ApplicationTestBuilder(testApplication),
    ClientProvider by testApplication

public fun withTestApplication1(
    externalServices: ExternalServicesBuilder.() -> Unit = {},
    block: suspend WithApplicationTestBuilder.() -> Unit
) {
    val externalServicesBuilder = ExternalServicesBuilder().apply(externalServices)
    val testApplication = TestApplication(externalServicesBuilder.externalApplication)
    WithApplicationTestBuilder(testApplication)
        .apply { runBlocking { block() } }

    testApplication.stop()
}

@Suppress("UNCHECKED_CAST")
public fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> ApplicationTestBuilder.install(
    plugin: Plugin<P, B, F>,
    configure: B.() -> Unit = {}
): F = application.install(plugin as Plugin<Application, B, F>, configure)

@ContextDsl
public fun ApplicationTestBuilder.routing(configuration: Routing.() -> Unit): Routing =
    application.routing(configuration)

public fun ApplicationTestBuilder.application(block: Application.() -> Unit) {
    application.block()
}
