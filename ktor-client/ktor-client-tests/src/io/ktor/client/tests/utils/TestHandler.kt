package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*

internal class TestHandler(
    private val resource: (cause: Throwable?) -> Unit
) {
    class Config {
        var onClose: (cause: Throwable?) -> Unit = {}
    }

    companion object Feature : HttpClientFeature<Config, TestHandler> {
        override val key: AttributeKey<TestHandler> = AttributeKey("Buffer")

        override fun prepare(block: Config.() -> Unit): TestHandler =
            Config().apply(block).let { TestHandler(it.onClose) }

        override fun install(feature: TestHandler, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { content: OutgoingContent.ReadChannelContent ->
                proceedWith(object : OutgoingContent.ReadChannelContent() {
                    override val headers: Headers = content.headers

                    override fun readFrom(): ByteReadChannel = writer(
                        Unconfined, autoFlush = true,
                        parent = context.executionContext
                    ) {
                        try {
                            content.readFrom().copyAndClose(channel)
                        } catch (cause: Throwable) {
                            feature.resource(cause)
                        } finally {
                            feature.resource(null)
                        }
                    }.channel
                })
            }
        }
    }
}
