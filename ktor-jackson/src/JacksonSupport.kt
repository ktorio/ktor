package org.jetbrains.ktor.jackson

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import java.io.*
import kotlin.reflect.*

data class JsonContent(val payload: Any?)
class RequestJsonContent internal constructor(private val mapper: ObjectMapper, private val stream: () -> InputStream) {
    fun <T : Any> get(kClass: KClass<T>): T? = mapper.readValue(stream(), kClass.java)
    inline fun <reified T : Any> get(): T? = get(T::class)
}

fun Application.setupJackson() {
    setupJackson { this }
}

fun Application.setupJackson(mapperConfigure: ObjectMapper.() -> ObjectMapper) {
    intercept(jacksonInterceptor(ObjectMapper().registerKotlinModule().mapperConfigure()))
}

fun RoutingEntry.setupJackson() {
    setupJackson { this }
}

fun RoutingEntry.setupJackson(mapperConfigure: ObjectMapper.() -> ObjectMapper) {
    intercept(jacksonInterceptor(ObjectMapper().registerKotlinModule().mapperConfigure()))
}

inline fun <reified T : Any> ApplicationRequest.withJsonContent(block: (T?) -> ApplicationRequestStatus): ApplicationRequestStatus =
        content.get<RequestJsonContent>().get<T>().let { block(it) }

inline fun <reified T : Any> ApplicationRequestContext.withJsonContent(block: (T?) -> ApplicationRequestStatus) : ApplicationRequestStatus =
    request.withJsonContent(block)

private fun <C : ApplicationRequestContext> jacksonInterceptor(mapper: ObjectMapper): C.(C.() -> ApplicationRequestStatus) -> ApplicationRequestStatus = { next ->
    response.interceptSend { message, next ->
        if (message is JsonContent) {
            response.contentType(ContentType.Application.Json)
            response.stream {
                mapper.writeValue(this, message.payload)
            }
            ApplicationRequestStatus.Handled
        } else {
            next(message)
        }
    }
    request.content.intercept { kClass, next ->
        when (kClass) {
            JsonContent::class -> JsonContent(mapper.readValue(request.content.get<InputStream>(), Any::class.java))
            RequestJsonContent::class -> RequestJsonContent(mapper) { request.content.get<InputStream>() }
            else -> next(kClass)
        }
    }

    next()
}

