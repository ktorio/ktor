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
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.withPort
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.Buffer
import kron.IncrementalOptions
import kron.KronServer
import kron.Reactor
import kron.ServerConfig
import kron.TcpOptions
import kron.tls.TlsOptions
import kron.tls.TlsService
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
 * Every connector listens on all IPv4 addresses, whatever its host. HTTPS is served on the engine's own connectors,
 * through kron's BoringSSL and, where asked for, kernel TLS: see [Configuration.tlsConnector].
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

        /**
         * Serves HTTP/1.1 over TLS on [port] with [options]: the certificate and key, the ALPN protocols (only
         * `http/1.1` is served), the lowest TLS version and kernel TLS. Each reactor makes its own TLS context.
         */
        public fun tlsConnector(port: Int, options: TlsOptions) {
            connectors.add(KronTlsConnector(port, options))
        }
    }

    // Each reactor's TLS services by the port they serve, made on the reactor's own thread, as BoringSSL wants.
    private var tlsServices = emptyArray<Map<Int, TlsService>?>()

    private var server: KronServer? = null

    /**
     * A job per reactor, and under each its connections' jobs and through them what each call starts: what [stop]
     * waits for, and then cancels.
     */
    internal lateinit var rootJob: CompletableJob
        private set

    // Resolved once a start outside development mode, where it never changes: on the JVM, EmbeddedServer hands it out
    // under a read lock, and that lock, taken by every reactor on every request, is one cache line all of them write.
    private var application: Application? = null

    internal fun application(): Application = application ?: applicationProvider()

    // Why a reactor could not start or went down, from its thread; and whether stop() has finished.
    private val fault = AtomicReference<Throwable?>(null)

    @Volatile private var stopped = false

    override fun start(wait: Boolean): ApplicationEngine {
        val connectors = configuration.connectors.toList()
        for (connector in connectors) {
            require(connector.type != ConnectorType.HTTPS || connector is KronTlsConnector) {
                "An HTTPS connector on kron is the engine's own: add it with tlsConnector(port, TlsOptions)"
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
                // No connector, which Ktor's other engines take as well, is reactors with nothing to listen on.
                tcp = ports.firstOrNull()?.let { port ->
                    TcpOptions(
                        port = port,
                        extraPorts = ports.drop(1).toIntArray(),
                        readTimeoutMs = configuration.readTimeoutMs,
                        sendTimeoutMs = configuration.sendTimeoutMs,
                    )
                },
            )

            val app = applicationProvider()
            application = app.takeUnless { it.developmentMode }
            val parent = app.parentCoroutineContext
            val job = SupervisorJob(parent[Job])
            rootJob = job

            // A coroutine a call leaves failing is logged: uncaught, Kotlin/Native would end the process.
            val uncaught = parent[CoroutineExceptionHandler] ?: DefaultUncaughtExceptionHandler(environment.log)

            // Each reactor reports in from its own thread once it listens, or why it could not start.
            val tlsOptions = connectors.indices
                .filter { connectors[it] is KronTlsConnector }
                .associate { ports[it] to (connectors[it] as KronTlsConnector).options }
            tlsServices = arrayOfNulls(configuration.reactorCount)

            val listening = AtomicInt(0)
            val onStart: (Reactor) -> Unit = { reactor ->
                if (tlsOptions.isNotEmpty()) {
                    tlsServices[reactor.id] = tlsOptions.mapValues { TlsService.start(reactor, it.value) }
                }
                listening.addAndFetch(1)
            }
            server = KronServer(config, onStart) { connection ->
                val tls = tlsServices[connection.reactor.id]?.get(connection.listenerPort)
                Http1Session(connection, this, tls).run()
            }

            // The job a handler runs under takes every cancellable wait of its calls - a delay() each - and each of its
            // connections' jobs: one job shared by all the reactors is one list they all add to and remove from.
            for (reactor in server.reactors) {
                reactor.handlerContext = parent + SupervisorJob(job) + uncaught + ReactorDispatcher(reactor)
                reactor.onFault = { _, e -> fault.compareAndSet(null, e) }
            }

            // On the JVM, kotlinx-io sizes its segment pool the first time it is used, by availableProcessors() - which
            // counts the calling thread's CPUs: first used on a pinned reactor, it would have one slot, every reactor
            // spinning on it.
            Buffer().run {
                writeByte(0)
                clear()
            }

            this.server = server.start()
            while (listening.load() < server.reactors.size) {
                val cause = fault.load()
                if (cause != null) {
                    server.close()
                    throw cause
                }

                sleepMillis(1)
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

                sleepMillis(50)
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
        while (rootJob.children.any { it.children.any() } && start.elapsedNow().inWholeMilliseconds < timeoutMs) {
            sleepMillis(1)
        }
    }

    private companion object {
        // How long cancelled calls get to finish, whatever the timeout: cancellation is quick, and the reactors
        // must outlive it.
        const val CANCEL_WAIT_MS = 1_000L
    }
}

/**
 * An HTTPS connector of the kron engine, on all IPv4 addresses: see [KronApplicationEngine.Configuration.tlsConnector].
 *
 * @property options the TLS port's settings, kron's own.
 */
public class KronTlsConnector(override val port: Int, public val options: TlsOptions) : EngineConnectorConfig {
    override val type: ConnectorType get() = ConnectorType.HTTPS
    override val host: String get() = "0.0.0.0"
}
