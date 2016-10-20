package org.jetbrains.ktor.transform

import java.util.*
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

class TransformTable<C : Any>(val parent: TransformTable<C>? = null) {
    private val topParent: TransformTable<C> = parent?.topParent ?: parent ?: this
    private val handlersCounter: AtomicInteger = parent?.handlersCounter ?: AtomicInteger()

    private val superTypesCacheLock = ReentrantReadWriteLock()
    private val superTypesCache = HashMap<Class<*>, Array<Class<*>>>()

    private val handlersLock = ReentrantReadWriteLock()
    private val handlers = HashMap<Class<*>, MutableList<Handler<C, *>>>()

    private val handlersCacheLock = ReentrantReadWriteLock()
    private val handlersCache = HashMap<Class<*>, List<Handler<C, *>>>()

    inline fun <reified T : Any> register(noinline handler: C.(T) -> Any) {
        register({ true }, handler)
    }

    inline fun <reified T : Any> register(noinline predicate: C.(T) -> Boolean, noinline handler: C.(T) -> Any) {
        register(T::class.javaObjectType, predicate, handler)
    }

    fun <T : Any> register(type: Class<T>, predicate: C.(T) -> Boolean, handler: C.(T) -> Any) {
        topParent.superTypes(type)
        addHandler(type, Handler(handlersCounter.getAndIncrement(), predicate, handler))

        handlersCacheLock.write {
            handlersCache.keys.filter { type.isAssignableFrom(it) }.forEach {
                handlersCache.remove(it)
            }
        }
    }

    fun <T : Any> handlers(type: Class<T>): List<Handler<C, T>> {
        val cached = handlersCacheLock.read { handlersCache[type] }
        val partialResult = if (cached == null) {
            val collected = collectHandlers(type)

            handlersCacheLock.write {
                handlersCache[type] = collected
            }

            collected
        } else {
            @Suppress("UNCHECKED_CAST")
            cached as List<Handler<C, T>>
        }

        return if (parent != null) {
            val parentResult = parent.handlers(type)
            when {
                parentResult.isEmpty() -> partialResult
                partialResult.isEmpty() -> parentResult
                else -> partialResult + parentResult
            }
        } else
            partialResult
    }

    class Handler<in C : Any, in T> internal constructor(val id: Int, val predicate: C.(T) -> Boolean, val handler: C.(T) -> Any) {
        override fun toString() = handler.toString()
    }

    fun newHandlersSet() = HandlersSet<C>()

    class HandlersSet<out C : Any> {
        private val bitSet = BitSet()

        fun add(element: Handler<C, *>): Boolean {
            if (bitSet[element.id]) {
                return false
            }

            bitSet[element.id] = true
            return true
        }

        fun remove(element: Handler<C, *>): Boolean {
            if (bitSet[element.id]) {
                bitSet[element.id] = false
                return true
            }

            return false
        }

        operator fun contains(element: Handler<C, *>) = bitSet[element.id]
    }

    private fun <T : Any> collectHandlers(type: Class<T>): List<Handler<C, T>> {
        val result = ArrayList<Handler<C, T>>(2)
        val superTypes = topParent.superTypes(type)

        handlersLock.read {
            for (superType in superTypes) {
                val hh = handlers[superType]
                if (hh != null && hh.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    result.addAll(hh as List<Handler<C, T>>)
                }
            }
        }

        return if (result.isEmpty()) emptyList() else result
    }

    private fun addHandler(type: Class<*>, handler: Handler<C, *>) {
        handlersLock.write {
            handlers.getOrPut(type) { ArrayList(2) }.add(handler)
        }
    }

    private fun superTypes(type: Class<*>): Array<Class<*>> = superTypesCacheLock.read {
        superTypesCache[type] ?: superTypesCacheLock.write {
            buildSuperTypes(type).apply { superTypesCache[type] = this }
        }
    }

    private fun buildSuperTypes(type: Class<*>) = dfs(type).asReversed().toTypedArray()
}
