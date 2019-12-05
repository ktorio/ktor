/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.channel.epoll.*
import io.netty.channel.kqueue.*
import kotlinx.coroutines.*
import io.netty.handler.codec.http.*
import java.util.concurrent.*
import kotlin.reflect.KClass

/**
 * [ApplicationEngine] implementation for running in a standalone Netty
 */
class NettyApplicationEngine(environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit = {}) :
    BaseApplicationEngine(environment) {

    /**
     * Configuration for the [NettyApplicationEngine]
     */
    class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Size of the queue to store [ApplicationCall] instances that cannot be immediately processed
         */
        var requestQueueLimit: Int = 16

        /**
         * Number of concurrently running requests from the same http pipeline
         */
        var runningLimit: Int = 10

        /**
         * Do not create separate call event group and reuse worker group for processing calls
         */
        var shareWorkGroup: Boolean = false

        /**
         * User-provided function to configure Netty's [ServerBootstrap]
         */
        var configureBootstrap: ServerBootstrap.() -> Unit = {}

        /**
         * Timeout in seconds for sending responses to client
         */
        var responseWriteTimeoutSeconds: Int = 10

        /**
         * Timeout in seconds for reading requests from client, "0" is infinite.
         */
        var requestReadTimeoutSeconds: Int = 0

        /**
         * User-provided function to configure Netty's [HttpServerCodec]
         */
        var httpServerCodec: () -> HttpServerCodec = ::HttpServerCodec
    }

    private val configuration = Configuration().apply(configure)

    /**
     * [EventLoopGroupProxy] for accepting connections
     */
    private val connectionEventGroup = EventLoopGroupProxy.create(configuration.connectionGroupSize)

    /**
     * [EventLoopGroupProxy] for processing incoming requests and doing engine's internal work
     */
    private val workerEventGroup = if (configuration.shareWorkGroup)
        EventLoopGroupProxy.create(configuration.workerGroupSize + configuration.callGroupSize)
    else
        EventLoopGroupProxy.create(configuration.workerGroupSize)

    /**
     * [EventLoopGroupProxy] for processing [ApplicationCall] instances
     */
    private val callEventGroup = if (configuration.shareWorkGroup)
        workerEventGroup
    else
        EventLoopGroupProxy.create(configuration.callGroupSize)

    private val dispatcherWithShutdown = DispatcherWithShutdown(NettyDispatcher)
    private val engineDispatcherWithShutdown = DispatcherWithShutdown(workerEventGroup.asCoroutineDispatcher())
    private var cancellationDeferred: CompletableJob? = null

    private var channels: List<Channel>? = null
    private val bootstraps = environment.connectors.map { connector ->
        ServerBootstrap().apply {
            configuration.configureBootstrap(this)
            group(connectionEventGroup, workerEventGroup)
            channel(connectionEventGroup.channel.java)
            childHandler(
                NettyChannelInitializer(
                    pipeline, environment,
                    callEventGroup, engineDispatcherWithShutdown, dispatcherWithShutdown,
                    connector,
                    configuration.requestQueueLimit,
                    configuration.runningLimit,
                    configuration.responseWriteTimeoutSeconds,
                    configuration.requestReadTimeoutSeconds,
                    configuration.httpServerCodec
                )
            )
        }
    }

    init {
        val afterCall = PipelinePhase("After")
        pipeline.insertPhaseAfter(EnginePipeline.Call, afterCall)
        pipeline.intercept(afterCall) {
            (call as? NettyApplicationCall)?.finish()
        }
    }

    override fun start(wait: Boolean): NettyApplicationEngine {
        environment.start()

        channels = bootstraps.zip(environment.connectors)
            .map { it.first.bind(it.second.host, it.second.port) }
            .map { it.sync().channel() }

        cancellationDeferred = stopServerOnCancellation()

        if (wait) {
            channels?.map { it.closeFuture() }?.forEach { it.sync() }
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        cancellationDeferred?.complete()
        environment.monitor.raise(ApplicationStopPreparing, environment)
        val channelFutures = channels?.mapNotNull { if (it.isOpen) it.close() else null }.orEmpty()

        dispatcherWithShutdown.prepareShutdown()
        engineDispatcherWithShutdown.prepareShutdown()
        try {
            val shutdownConnections =
                connectionEventGroup.shutdownGracefully(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
            shutdownConnections.await()

            val shutdownWorkers =
                workerEventGroup.shutdownGracefully(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
            if (configuration.shareWorkGroup) {
                shutdownWorkers.await()
            } else {
                val shutdownCall =
                    callEventGroup.shutdownGracefully(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
                shutdownWorkers.await()
                shutdownCall.await()
            }

            environment.stop()
        } finally {
            dispatcherWithShutdown.completeShutdown()
            engineDispatcherWithShutdown.completeShutdown()

            channelFutures.forEach { it.sync() }
        }
    }

    override fun toString(): String {
        return "Netty($environment)"
    }
}

/**
 * Transparently allows for the creation of [EventLoopGroup]'s utilising the optimal implementation for
 * a given operating system, subject to availability, or falling back to [NioEventLoopGroup] if none is available.
 */
class EventLoopGroupProxy(val channel: KClass<out ServerSocketChannel>, group: EventLoopGroup) : EventLoopGroup by group {

    companion object {

        fun create(parallelism: Int): EventLoopGroupProxy {
            return when {
                KQueue.isAvailable() -> EventLoopGroupProxy(KQueueServerSocketChannel::class, KQueueEventLoopGroup(parallelism))
                Epoll.isAvailable() -> EventLoopGroupProxy(EpollServerSocketChannel::class, EpollEventLoopGroup(parallelism))
                else -> EventLoopGroupProxy(NioServerSocketChannel::class, NioEventLoopGroup(parallelism))
            }
        }
    }
}
