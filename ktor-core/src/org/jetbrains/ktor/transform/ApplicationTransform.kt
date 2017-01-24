package org.jetbrains.ktor.transform

import kotlin.reflect.*

class ApplicationTransform<C : Any>(private val parent: TransformTable<C>? = null) {
    var table: TransformTable<C> = parent ?: TransformTable()
        private set

    inline fun <reified T : Any> register(handler: suspend C.(T) -> Any) {
        register({ true }, handler)
    }

    inline fun <reified T : Any> register(noinline predicate: C.(T) -> Boolean,  handler: suspend C.(T) -> Any) {
        register(T::class, predicate, handler)
    }

    fun <T : Any> register(type: KClass<T>, predicate: C.(T) -> Boolean, handler: suspend C.(T) -> Any) {
        register(type.javaObjectType, predicate, handler)
    }

    fun <T : Any> register(type: Class<T>, predicate: C.(T) -> Boolean, handler:suspend C.(T) -> Any) {
        if (table === parent) {
            table = TransformTable(parent)
        }

        table.register(type, predicate, handler)
    }

    fun <T : Any> handlers(type: Class<T>) = table.handlers(type)
    inline fun <reified T : Any> handlers() = handlers(T::class.javaObjectType)
}