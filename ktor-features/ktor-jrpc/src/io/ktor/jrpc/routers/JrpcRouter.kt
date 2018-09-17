package io.ktor.jrpc.routers

import com.google.gson.JsonParseException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.jrpc.JrpcSupport
import io.ktor.jrpc.model.*
import io.ktor.pipeline.PipelineContext
import io.ktor.request.contentCharset
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.util.pipeline.ContextDsl
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Type of HashMap<String, JrpcRouteItem<*>>
 */
typealias JrpcMethodRouter = HashMap<String, JrpcRouteItem<*>>

/**
 * JSON-RPC router class
 * Use GSON from [JrpcSupport] for converting JRPC body
 * Handle JRPC-Requests using configured handlers
 * Before JRPC and After JRPC hooks can be set
 */
open class JrpcRouter {

    private val gson = JrpcSupport.gson

    private val methodHandlers = JrpcMethodRouter(100, 0.3f) //faster

    private var beforeJrpcHandler: (PipelineContext<Unit, ApplicationCall>.(JrpcRequest) -> JrpcResponse?)? = null

    private var afterJrpcHandler: (PipelineContext<Unit, ApplicationCall>.(JrpcRequest, JrpcResponse) -> JrpcResponse?)? = null

    /**
     * Test if JRPC method is already in methodHandlers
     * @param method name of the JRPC method
     */
    fun methodAlreadySet(method: String) = method in methodHandlers

    /**
     * Add JRPC route item to method handlers
     * @param method name of the JRPC method
     * @param routeItem [JrpcRouteItem]
     */
    fun addMethod(method: String, routeItem: JrpcRouteItem<*>) {
        methodHandlers[method] = routeItem
    }

    /**
     * HTTP POST JRPC handler
     * Receive JrpcRequest from context.call using ktor-gson
     * If beforeJrpcHandler is set, will call it before processRequest. If beforeJrpcHandler return JrpcResponse,
     * will return it instead calling processRequest
     * After processing processRequest will call afterJrpcHandler if it set. If afterJrpcHandler return JrpcResponse,
     * will return it instead result of calling processRequest
     * @param context the pipeline context
     */
    suspend fun jrpcPostHandler(context: PipelineContext<Unit, ApplicationCall>) {
        var jrpcRequestId: Long? = null
        val result: JrpcResponse = try {
            val jrpcRequest: JrpcRequest? = if (JrpcSupport.useInternalSerialization) {
                context.call.request.receiveChannel()
                        .toInputStream()
                        .reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
                        .use { reader ->
                            gson.fromJson(reader, JrpcRequest::class.java)
                        }
            } else {
                context.call.receiveOrNull()
            }
            jrpcRequestId = jrpcRequest?.id
            if (jrpcRequest != null) {
                val processResult = beforeJrpcHandler?.invoke(context, jrpcRequest)
                        ?: processRequest(context, jrpcRequest)
                afterJrpcHandler?.invoke(context, jrpcRequest, processResult) ?: processResult
            } else {
                JrpcResponse(jrpcRequestId, error = JrpcError.INVALID_REQUEST)
            }
        } catch (e: Exception) {
            val error = if (e is JsonParseException) JrpcError.PARSE_ERROR else JrpcError.INTERNAL_ERROR
            if (JrpcSupport.printStackTraces) {
                JrpcResponse(jrpcRequestId, error = JrpcError(stackTraceToString(e), error.code))
            } else {
                JrpcResponse(jrpcRequestId, error = error)
            }
        }
        context.call.respond(if (JrpcSupport.useInternalSerialization) gson.toJson(result) else result)
    }

    /**
     * Process JRPC-request, finding handler for this request,
     * trying to convert params from JrpcRequest to type requested by method
     * @param context the pipeline context
     * @param jrpcRequest [JrpcRequest] that must be handled
     * @return JrpcResponse containing the result of handling or error if any
     */
    private fun processRequest(context: PipelineContext<Unit, ApplicationCall>, jrpcRequest: JrpcRequest): JrpcResponse {
        val requestId = jrpcRequest.id
        val jrpcRouteItem = methodHandlers[jrpcRequest.method]
        return if (jrpcRouteItem != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val handler = (jrpcRouteItem.handler as PipelineContext<Unit, ApplicationCall>.(Any) -> Any?)
                val clazz = jrpcRouteItem.clazz
                if (jrpcRouteItem.clazz == Unit::class.java) {
                    JrpcResponse(requestId, handler(context, Unit))
                } else {
                    val params = jrpcRequest.params
                    if (params == null) {
                        JrpcResponse(requestId, error = JrpcError.INVALID_PARAMS_NOT_UNIT)
                    } else {
                        val methodParams = try {
                            gson.fromJson(params, clazz)
                        } catch (e: JsonParseException) {
                            null
                        }
                        if (methodParams == null) {
                            JrpcResponse(requestId, error = JrpcError.INVALID_PARAMS)
                        } else {
                            JrpcResponse(requestId, handler(context, methodParams))
                        }
                    }
                }
            } catch (je: JrpcException) {
                JrpcResponse(requestId, error = JrpcError("${je.message}:\r\n${
                if (JrpcSupport.printStackTraces) stackTraceToString(je) else "stacktrace hidden"
                }", je.code))
            } catch (cce: ClassCastException) {
                JrpcResponse(requestId, error = JrpcError("error calling ${jrpcRequest.method} ${cce.localizedMessage}. Handler can not be cast to PipelineContext<Unit, ApplicationCall>.(Any) -> Any?", JrpcError.CODE_INTERNAL_ERROR))
            } catch (e: Exception) {
                JrpcResponse(requestId, error = JrpcError("error calling ${jrpcRequest.method} ${e.localizedMessage}", JrpcError.CODE_INTERNAL_ERROR))
            }
        } else {
            JrpcResponse(requestId, error = JrpcError.METHOD_NOT_FOUND)
        }
    }

    private fun stackTraceToString(throwable: Throwable): String {
        var result = ""
        StringWriter().use { sw ->
            PrintWriter(sw).use { pw ->
                throwable.printStackTrace(pw)
            }
            result = sw.toString()
        }
        return result
    }

    /**
     * Add new JRPC-method for handling. The param will converted to requested type if it possible.
     * The result will be added to result of JrpcResponse
     * If you need to throw from method, you may use [JrpcException]
     * @param T type of input argument to which will be converted "params" key from JRPC-request. Pass Unit if you don't have any input params, pass Any? if you want to convert if by yourself
     * @param method name of JRPC method
     * @param block method body with requested receiver T
     * @throws RuntimeException if method with the same name exists in handlers
     */
    @ContextDsl
    inline fun <reified T : Any> method(method: String, noinline block: PipelineContext<Unit, ApplicationCall>.(T) -> Any?) {
        if (methodAlreadySet(method)) throw RuntimeException("Method with name \"$method\" already exists in handlers")
        else addMethod(method, JrpcRouteItem(block, T::class.java))
    }

    /**
     * If handler parse JrpcRequest properly, will called before handle any JRPC-method
     * Usable for checks, headers, etc
     * @param block - block of code that will be executed. If block return JrpcResponse, the other JRPC methods will not be handled, the method returns immediately with that JrpcResponse. Note that params will be not parsed at the moment.
     * @throws RuntimeException if called twice
     */
    @ContextDsl
    fun beforeJrpc(block: PipelineContext<Unit, ApplicationCall>.(JrpcRequest) -> JrpcResponse?) {
        if (beforeJrpcHandler != null) throw RuntimeException("Before Jrpc Handler is already set")
        else beforeJrpcHandler = block
    }

    /**
     * If handler parse JrpcRequest properly, will called after handle any JRPC-method
     * Usable for modifying JRPC-responses, logging responses, etc.
     * @param block - block of code that will be executed. If block return JrpcResponse, the method returns immediately with that JrpcResponse instead of result of method handling
     * @throws RuntimeException if called twice
     */
    @ContextDsl
    fun afterJrpc(block: PipelineContext<Unit, ApplicationCall>.(JrpcRequest, JrpcResponse) -> JrpcResponse?) {
        if (afterJrpcHandler != null) throw RuntimeException("After Jrpc Handler is already set")
        else afterJrpcHandler = block
    }
}