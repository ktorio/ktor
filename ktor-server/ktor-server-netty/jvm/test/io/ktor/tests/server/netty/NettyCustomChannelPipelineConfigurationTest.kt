/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.http1.NettyHttp1ApplicationCall
import io.ktor.server.response.*
import io.ktor.server.test.base.*
import io.netty.channel.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.*

abstract class NettyCustomChannelTest<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @OptIn(ExperimentalAtomicApi::class)
    val pipelineHandlerNames = AtomicReference<List<String>>(emptyList())
    var counter = 0

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testCustomChannelHandlerInvoked() = runTest {
        createAndStartServer {
            handle {
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.OK.value, status.value)
            assertNotEquals(0, counter)
            val uniqueNames = mutableSetOf<String>()
            if (pipelineHandlerNames.load().any { !uniqueNames.add(it) }) {
                fail("Pipeline handlers are not unique")
            }
        }
    }
}

class NettyCustomChannelPipelineConfigurationTest :
    NettyCustomChannelTest<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    @OptIn(ExperimentalAtomicApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
        configuration.channelPipelineConfig = {
            addLast(
                "customHandler",
                object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        counter = counter.plus(1)
                        if (msg is NettyHttp1ApplicationCall) {
                            pipelineHandlerNames.store(ctx.pipeline().names().map { it.substringBefore('#') })
                        }
                        super.channelRead(ctx, msg)
                    }
                }
            )
        }
    }
}
