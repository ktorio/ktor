package org.jetbrains.ktor.jetty

import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.util.thread.*
import kotlin.coroutines.experimental.*

internal class JettyCoroutinesDispatcher(
        private val threadPool: ThreadPool
) : CoroutineDispatcher() {

    private val poolName = (threadPool as? QueuedThreadPool)?.name

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return poolName == null || !Thread.currentThread().name.startsWith(poolName)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) = threadPool.execute(block)
}
