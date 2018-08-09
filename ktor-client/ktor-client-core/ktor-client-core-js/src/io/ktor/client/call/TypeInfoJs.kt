package io.ktor.client.call


actual interface Type

object JsType : Type

actual inline fun <reified T> typeInfo(): TypeInfo {
    return TypeInfo(T::class, JsType)
}
