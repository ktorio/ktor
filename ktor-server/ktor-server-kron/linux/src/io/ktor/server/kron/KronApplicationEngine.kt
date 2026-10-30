/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.events.Events
import io.ktor.events.raiseCatching
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ServerReady
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.BaseApplicationEngine
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.DefaultUncaughtExceptionHandler
import io.ktor.server.engine.withPort
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kron.IncrementalOptions
import kron.KronServer
import kron.Reactor
import kron.ServerConfig
import kron.TcpOptions
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.SO_REUSEPORT
import platform.posix.bind
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.usleep
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.TimeSource

/** Ktor's engine factory for kron: `embeddedServer(Kron, port = 8080) { ... }`. */
public object Kron : ApplicationEngineFactory<KronApplicationEngine, KronApplicationEngine.Configuration> {
    override fun configuration(
        configure: KronApplicationEngine.Configuration.() -> Unit
    ): KronApplicationEngine.Configuration =
        KronApplicationEngine.Configuration().apply(configure)

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: KronApplicationEngine.Configuration,
        applicationProvider: () -> Application,
    ): KronApplicationEngine = KronApplicationEngine(
        environment,
        monitor,
        developmentMode,
        configuration,
        applicationProvider
    )
}

/**
 * Ktor on kron's reactors. Each reactor accepts its own connections and runs their calls on its own thread, from
 * the parse through the application's pipeline to the send: nothing is handed between threads, and a handler that
 * blocks holds its reactor, as on any kron server.
 *
 * Every connector listens on all IPv4 addresses, whatever its host; HTTPS connectors are refused.
 */
public class KronApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    public val configuration: Configuration,
    internal val applicationProvider: () -> Application,
) : BaseApplicationEngine(environment, monitor, developmentMode) {

    /**
     * @property reactorCount reactors, one thread and one ring each.
     * @property pinReactors each reactor on a CPU of its own, see [ServerConfig.pinReactors].
     * @property incremental a buffer ring per connection, see [ServerConfig.incremental].
     * @property readTimeoutMs how long a connection may wait on a silent peer; 0 disables it.
     * @property sendTimeoutMs how long a send may wait on a peer that stopped draining; 0 disables it.
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        public var reactorCount: Int = parallelism
        public var pinReactors: Boolean = false
        public var incremental: IncrementalOptions? = null
        public var readTimeoutMs: Long = 60_000
        public var sendTimeoutMs: Long = 60_000
    }

    private var server: KronServer? = null

    /** Every connection's job, and through it what each call starts: what [stop] waits for, and then cancels. */
    internal lateinit var rootJob: CompletableJob
        private set

    // Why a reactor could not start or went down, from its thread; and whether stop() has finished.
    private val fault = AtomicReference<Throwable?>(null)

    @Volatile private var stopped = false

    override fun start(wait: Boolean): ApplicationEngine {
        val connectors = configuration.connectors.toList()
        require(connectors.isNotEmpty()) { "kron needs a connector to listen on" }
        for (connector in connectors) {
            if (connector.type == ConnectorType.HTTPS) {
                throw UnsupportedOperationException("The kron engine does not support HTTPS connectors yet")
            }
        }

        // Port 0 is the kernel's pick, made once here for every reactor: on their own, each would get a port of its own.
        val reserved = ArrayList<ReservedPort>()
        val server: KronServer
        try {
            val ports = connectors.map { if (it.port != 0) it.port else ReservedPort().also(reserved::add).port }
            val config = ServerConfig(
                reactorCount = configuration.reactorCount,
                pinReactors = configuration.pinReactors,
                incremental = configuration.incremental,
                tcp = TcpOptions(
                    port = ports[0],
                    extraPorts = ports.drop(1).toIntArray(),
                    readTimeoutMs = configuration.readTimeoutMs,
                    sendTimeoutMs = configuration.sendTimeoutMs,
                ),
            )

            val parent = applicationProvider().parentCoroutineContext
            val job = SupervisorJob(parent[Job])
            rootJob = job

            // A coroutine a call leaves failing is logged: uncaught, Kotlin/Native would end the process.
            val uncaught = parent[CoroutineExceptionHandler] ?: DefaultUncaughtExceptionHandler(environment.log)

            // Each reactor reports in from its own thread once it listens, or why it could not start.
            val listening = AtomicInt(0)
            server = KronServer(config, onStart = { listening.addAndFetch(1) }) { connection ->
                Http1Session(connection, this).run()
            }

            for (reactor in server.reactors) {
                reactor.handlerContext = parent + job + uncaught + ReactorDispatcher(reactor)
                reactor.onFault = { _, e -> fault.compareAndSet(null, e) }
            }

            this.server = server.start()
            while (listening.load() < server.reactors.size) {
                val cause = fault.load()
                if (cause != null) {
                    server.close()
                    throw cause
                }

                usleep(1_000u)
            }

            resolvedConnectorsDeferred.complete(connectors.mapIndexed { i, connector -> connector.withPort(ports[i]) })
        } finally {
            reserved.forEach(ReservedPort::close)
        }

        monitor.raiseCatching(ServerReady, environment, environment.log)

        if (wait) {
            // Polled rather than joined: Ktor stops the engine from its SIGINT and SIGTERM handler, which can run on
            // this very thread - from inside join() it would wait for a flag the interrupted join holds.
            while (!stopped) {
                fault.load()?.let { cause ->
                    server.close()
                    throw cause
                }

                usleep(50_000u)
            }

            server.join()
        }

        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        val server = server ?: return
        this.server = null
        monitor.raiseCatching(ApplicationStopPreparing, environment, environment.log)

        // No new connections, and each open one ends after its current response. What is left when the grace period
        // is over is cancelled while the reactors still run to carry the cancellation out, and only then do they
        // stop: a coroutine resumed after its reactor has gone would never run. On a reactor thread nothing is
        // waited for, as that reactor could make no progress meanwhile.
        server.reactors.forEach(Reactor::stopAccepting)
        val onReactorThread = server.reactors.any { it.onReactorThread }
        if (!onReactorThread) {
            awaitConnections(gracePeriodMillis)
        }

        rootJob.cancel()
        if (!onReactorThread) {
            awaitConnections(maxOf(timeoutMillis - gracePeriodMillis, CANCEL_WAIT_MS))
        }

        server.close()
        stopped = true
    }

    // Polled rather than awaited, as stop() can run in a signal handler: until every connection, and what its calls
    // started, has ended, or timeoutMs has passed.
    private fun awaitConnections(timeoutMs: Long) {
        val start = TimeSource.Monotonic.markNow()
        while (rootJob.children.any() && start.elapsedNow().inWholeMilliseconds < timeoutMs) {
            usleep(1_000u)
        }
    }

    private companion object {
        // How long cancelled calls get to finish, whatever the timeout: cancellation is quick, and the reactors
        // must outlive it.
        const val CANCEL_WAIT_MS = 1_000L
    }
}

/**
 * A port the kernel picks, held by a socket that binds it with SO_REUSEPORT and never listens: every reactor's
 * listener can bind it too, nothing else can take it until [close], and no connection is ever queued on it.
 */
private class ReservedPort : AutoCloseable {
    private val fd = socket(AF_INET, SOCK_STREAM, 0)
    val port: Int

    init {
        check(fd >= 0) { "socket failed with errno $errno" }
        port = memScoped {
            val on = alloc<IntVar>().apply { value = 1 }
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, on.ptr, sizeOf<IntVar>().convert())
            setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, on.ptr, sizeOf<IntVar>().convert())

            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.convert()
            val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            if (bind(fd, address.ptr.reinterpret(), length.value) != 0 ||
                getsockname(fd, address.ptr.reinterpret(), length.ptr) != 0
            ) {
                val e = errno
                platform.posix.close(fd)
                throw IllegalStateException("Could not reserve a port: errno $e")
            }

            // sin_port is in network order
            val p = address.sin_port.toInt()
            ((p and 0xFF) shl 8) or ((p ushr 8) and 0xFF)
        }
    }

    override fun close() {
        platform.posix.close(fd)
    }
}
