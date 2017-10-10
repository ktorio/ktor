package io.ktor.servlet

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.host.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

open class ServletApplicationCall(application: Application,
                                  val servletRequest: HttpServletRequest,
                                  val servletResponse: HttpServletResponse,
                                  override val bufferPool: ByteBufferPool,
                                  hostContext: CoroutineContext,
                                  userAppContext: CoroutineContext) : BaseApplicationCall(application) {

    override val request: ServletApplicationRequest = ServletApplicationRequest(this, servletRequest)
    override val response: ServletApplicationResponse = ServletApplicationResponse(this, servletRequest, servletResponse, hostContext, userAppContext)
}