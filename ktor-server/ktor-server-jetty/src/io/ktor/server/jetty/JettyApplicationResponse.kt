package io.ktor.server.jetty

import io.ktor.content.*
import io.ktor.response.*
import io.ktor.server.jetty.internal.*
import io.ktor.server.servlet.*
import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.server.*
import java.util.concurrent.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

class JettyApplicationResponse(call: ServletApplicationCall,
                               servletRequest: HttpServletRequest,
                               servletResponse: HttpServletResponse,
                               hostCoroutineContext: CoroutineContext,
                               userCoroutineContext: CoroutineContext,
                               private val baseRequest: Request)
    : ServletApplicationResponse(call, servletRequest, servletResponse, hostCoroutineContext, userCoroutineContext, JettyUpgradeImpl) {

    override fun push(builder: ResponsePushBuilder) {
        if (baseRequest.isPushSupported) {
            baseRequest.pushBuilder.apply {
                this.method(builder.method.value)
                this.path(builder.url.encodedPath)
                val query = builder.url.build().substringAfter('?', "").takeIf { it.isNotEmpty() }
                if (query != null) {
                    queryString(query)
                }

                push()
            }
        } else {
            super.push(builder)
        }
    }
}