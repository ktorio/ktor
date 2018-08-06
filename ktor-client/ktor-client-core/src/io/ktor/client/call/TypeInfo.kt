package io.ktor.client.call

import kotlin.reflect.*

expect interface Type

/**
 * Ktor type information.
 * [type]: source KClass<*>
 * [reifiedType]: type with substituted generics
 */
data class TypeInfo(val type: KClass<*>, val reifiedType: Type)

expect inline fun <reified T> typeInfo(): TypeInfo
