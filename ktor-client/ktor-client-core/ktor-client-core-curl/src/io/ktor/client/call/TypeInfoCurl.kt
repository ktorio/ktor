package io.ktor.client.call


actual interface Type {}

object CurlType : Type {}

@PublishedApi()
internal open class TypeBase<T>

actual inline fun <reified T> typeInfo(): TypeInfo {
    val kClass = T::class
    return TypeInfo(kClass, CurlType)
}
