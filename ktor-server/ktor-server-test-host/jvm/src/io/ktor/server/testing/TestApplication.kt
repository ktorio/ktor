/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import com.typesafe.config.*
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.server.testing.client.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*

public interface ClientProvider {
    public val client: HttpClient
    public fun createClient(block: HttpClientConfig<TestHttpClientConfig>.() -> Unit): HttpClient
}

public class TestApplication internal constructor() : ClientProvider {

    internal val config = HoconApplicationConfig(ConfigFactory.load())
    internal val environment = createTestEnvironment {
        config = this@TestApplication.config
    }
    private val engine = TestApplicationEngine(environment = this@TestApplication.environment)
    public val application = engine.application

    public override val client by lazy { createClient { } }

    public override fun createClient(block: HttpClientConfig<TestHttpClientConfig>.() -> Unit): HttpClient {
        return HttpClient(TestHttpClientEngine) {
            engine {
                app = engine
            }
            block()
        }
    }

    public fun start() {
        engine.start()
    }

    public fun stop() {
        engine.stop(0, 0)
    }
}

public fun TestApplication(block: ApplicationTestBuilder.() -> Unit): TestApplication {
    val testApplication = TestApplication()
    val builder = ApplicationTestBuilder(testApplication)
    builder.block()
    return testApplication
}

public open class ApplicationTestBuilder(private val testApplication: TestApplication) {
    public val config: ApplicationConfig = testApplication.config
    public val environment: ApplicationEnvironment = testApplication.environment
    public val application: Application = testApplication.application
}

public class WithApplicationTestBuilder(private val testApplication: TestApplication) :
    ApplicationTestBuilder(testApplication),
    ClientProvider by testApplication {
}

public fun withTestApplication1(block: suspend WithApplicationTestBuilder.() -> Unit) {
    val testApplication = TestApplication()
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
