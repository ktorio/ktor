package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*
import kotlin.reflect.*

public abstract class ApplicationRequestContent(private val request: ApplicationRequest) {
    private val contentsChain = Interceptable1<KClass<*>, Any> { type ->
        when (type) {
            InputStream::class -> getInputStream()
            String::class -> getInputStream().reader(request.contentCharset ?: Charsets.ISO_8859_1).readText()
            else -> throw UnknownContentAccessorRequest("Requested content accessor '$type' cannot be provided")
        }
    }

    protected abstract fun getInputStream(): InputStream

    public final fun intercept(handler: (type: KClass<*>, next: (type: KClass<*>) -> Any) -> Any) {
        contentsChain.intercept(handler)
    }

    @Suppress("UNCHECKED_CAST")
    public operator fun <T : Any> get(type: KClass<T>): T = contentsChain.call(type) as T
    public inline fun <reified T : Any> get(): T = get(T::class)
}

public class UnknownContentAccessorRequest(message: String) : Exception(message)
