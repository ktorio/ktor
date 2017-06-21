package org.jetbrains.ktor.testing

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import java.io.*
import java.time.*
import java.util.concurrent.*

class TestApplicationCall(application: Application) : BaseApplicationCall(application) {
    init {
        sendPipeline.intercept(ApplicationSendPipeline.Host) {
            requestHandled = true
            response.close()
        }
    }

    override val request: TestApplicationRequest = TestApplicationRequest(this)
    override val response = TestApplicationResponse(this)

    @Volatile
    var requestHandled = false

    private val webSocketCompleted = CountDownLatch(1)

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        upgrade.upgrade(request.receive<ReadChannel>(), response.realContent.value, Closeable { webSocketCompleted.countDown() }, CommonPool, Unconfined)
    }

    override suspend fun responseChannel(): WriteChannel = response.realContent.value.apply {
        response.headers[HttpHeaders.ContentLength]?.let { contentLengthString ->
            val contentLength = contentLengthString.toLong()
            if (contentLength >= Int.MAX_VALUE) {
                throw IllegalStateException("Content length is too big for test host")
            }

            ensureCapacity(contentLength.toInt())
        }
    }

    fun awaitWebSocket(duration: Duration) {
        if (!webSocketCompleted.await(duration.toMillis(), TimeUnit.MILLISECONDS))
            throw TimeoutException()
    }
}