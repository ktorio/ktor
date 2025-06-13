/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.cors.routing.cors
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CORSTest {
    @Test
    fun installedInRouting() = testApplication {
        serverConfig {
            developmentMode = false
        }

        routing {
            cors()

//            val optionsParam = "static-options-param"
//            val rootNode = this as RoutingNode
//            createChild(object : RouteSelector() {
//                override suspend fun evaluate(
//                    context: RoutingResolveContext,
//                    segmentIndex: Int
//                ): RouteSelectorEvaluation =
//                    RouteSelectorEvaluation.Success(quality = RouteSelectorEvaluation.qualityTailcard)
//
//                override fun toString() = "(CORS Options)"
//            }).apply {
//                route("{$optionsParam...}") {
//                    options {
//                        val method = HttpMethod.Put // TODO: Get from HttpHeaders.AccessControlRequestMethod
//                        val pipelineCall = call.pipelineCall
//                        val request = pipelineCall.request
//                        val local = request.local
//
//                        val newMethodCall = object : PipelineCall {
//                            override val request: PipelineRequest
//                                get() = object : PipelineRequest {
//                                    override val call: PipelineCall
//                                        get() = request.call
//                                    override val pipeline: ApplicationReceivePipeline
//                                        get() = request.pipeline
//
//                                    @InternalAPI
//                                    override fun setHeader(
//                                        name: String,
//                                        values: List<String>?
//                                    ) {
//                                        request.setHeader(name, values)
//                                    }
//
//                                    @InternalAPI
//                                    override fun setReceiveChannel(channel: ByteReadChannel) {
//                                        request.setReceiveChannel(channel)
//                                    }
//
//                                    override val headers: Headers
//                                        get() = TODO("Not yet implemented")
//                                    override val local: RequestConnectionPoint
//                                        get() = object : RequestConnectionPoint {
//                                            override val scheme: String
//                                                get() = local.scheme
//                                            override val version: String
//                                                get() = local.version
//                                            override val port: Int
//                                                get() = local.localPort
//                                            override val localPort: Int
//                                                get() = local.localPort
//                                            override val serverPort: Int
//                                                get() = local.serverPort
//                                            override val host: String
//                                                get() = local.localHost
//                                            override val localHost: String
//                                                get() = local.localHost
//                                            override val serverHost: String
//                                                get() = local.serverHost
//                                            override val localAddress: String
//                                                get() = local.localAddress
//                                            override val uri: String
//                                                get() = local.uri
//                                            override val method: HttpMethod
//                                                get() = method
//                                            override val remoteHost: String
//                                                get() = local.remoteHost
//                                            override val remotePort: Int
//                                                get() = local.remotePort
//                                            override val remoteAddress: String
//                                                get() = local.remoteAddress
//                                        }
//                                    override val queryParameters: Parameters
//                                        get() = request.queryParameters
//                                    override val rawQueryParameters: Parameters
//                                        get() = request.rawQueryParameters
//                                    override val cookies: RequestCookies
//                                        get() = request.cookies
//
//                                    override fun receiveChannel(): ByteReadChannel {
//                                        return request.receiveChannel()
//                                    }
//
//                                }
//                            override val response: PipelineResponse
//                                get() = pipelineCall.response
//                            override val attributes: Attributes
//                                get() = pipelineCall.attributes
//                            override val application: Application
//                                get() = pipelineCall.application
//                            override val parameters: Parameters
//                                get() = pipelineCall.parameters
//                            override val coroutineContext: CoroutineContext
//                                get() = pipelineCall.coroutineContext
//                        }
//
//                        val resolveContext = RoutingResolveContext(rootNode, newMethodCall, emptyList())
//                        val result = resolveContext.resolve()
//
//                        if (result is RoutingResolveResult.Success) {
//                            call.respond(HttpStatusCode.OK)
//                            // TODO: Preflight response logic
//                        } else {
//                            call.respond(HttpStatusCode.NotFound)
//                        }
//                    }
//                }
//            }
//
//            install(CORS) {
//                anyHost()
//                allowMethod(HttpMethod.Put)
//            }
//            options("test") { }
            put("test") {
                call.respond("Hello World")
            }
        }

        val response = client.options("/test") {
            headers.append("Origin", "https://example.com")
            headers.append("Access-Control-Request-Method", "PUT")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HttpStatusCode.NotFound, client.options("/nonexistent").status)
    }
}
