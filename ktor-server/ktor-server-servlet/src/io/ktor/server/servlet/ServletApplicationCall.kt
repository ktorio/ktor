package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.server.host.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

open class ServletApplicationCall(application: Application,
                                  servletRequest: HttpServletRequest,
                                  servletResponse: HttpServletResponse,
                                  override val bufferPool: ByteBufferPool,
                                  hostContext: CoroutineContext,
                                  userAppContext: CoroutineContext,
                                  upgrade: ServletUpgrade) : BaseApplicationCall(application) {

    override val request: ServletApplicationRequest = ServletApplicationRequest(this, servletRequest)

    override val response: ServletApplicationResponse = ServletApplicationResponse(this,
            servletRequest, servletResponse, hostContext, userAppContext, upgrade)
}