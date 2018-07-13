package io.ktor.client.call

import java.lang.reflect.*
import kotlin.reflect.*

/**
 * Ktor type information.
 * [type]: source KClass<*>
 * [reifiedType]: type with substituted generics
 */
data class TypeInfo(val type: KClass<*>, val reifiedType: Type)

@PublishedApi()
internal open class TypeBase<T>

/**
 * Create typeInfo from <T>
 */
inline fun <reified T> typeInfo(): TypeInfo {
    val base = object : TypeBase<T>() {}
    val superType = base::class.java.genericSuperclass!!

    val reifiedType = (superType as ParameterizedType).actualTypeArguments.first()!!
    return TypeInfo(T::class, reifiedType)
}
