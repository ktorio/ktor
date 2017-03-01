package org.jetbrains.ktor.transform

import org.jetbrains.ktor.util.*
import java.util.*
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.*
import kotlin.collections.ArrayList
import kotlin.concurrent.*

class TransformTable<TContext : Any>(val parent: TransformTable<TContext>? = null) {
    private val registrationCounter = AtomicInteger()
    private val registrationsLock = ReentrantReadWriteLock()
    private val registrations = HashMap<Class<*>, Any>()

    private var handlersCacheCounter: Int = registrationCounter.get()
    private val handlersCacheLock = ReentrantReadWriteLock()
    private var handlersCache = HashMap<Class<*>, List<Handler<TContext, *>>>()

    inline fun <reified T : Any> register(noinline handler: suspend TContext.(T) -> Any) {
        register({ true }, handler)
    }

    inline fun <reified T : Any> register(noinline predicate: TContext.(T) -> Boolean, noinline handler: suspend TContext.(T) -> Any) {
        register(T::class.javaObjectType, predicate, handler)
    }

    fun <T : Any> register(type: Class<T>, predicate: TContext.(T) -> Boolean, handler: suspend TContext.(T) -> Any) {
        val entry = Handler(this, registrationCounter.getAndIncrement(), predicate, handler)
        registrationsLock.write {
            val current = registrations[type]

            @Suppress("UNCHECKED_CAST")
            when (current) {
                null -> registrations[type] = entry
                is Handler<*, *> -> registrations[type] = ArrayList<Handler<TContext, *>>(2).apply {
                    add(current as Handler<TContext, *>)
                    add(entry)
                }
                is ArrayList<*> -> (current as ArrayList<Handler<TContext, *>>).add(entry)
                else -> throw IllegalStateException("Unknown entry in registrations table: $current")
            }
        }

        handlersCacheLock.write {
            val combinedRegistrationCounter = registrationCounter.get() + (parent?.registrationCounter?.get() ?: 0)
            handlersCacheCounter = combinedRegistrationCounter
            handlersCache.clear()
        }
    }

    fun <T : Any> handlers(type: Class<out T>): List<Handler<TContext, T>> {
        val cached = handlersCacheLock.read {
            val combinedRegistrationCounter = registrationCounter.get() + (parent?.registrationCounter?.get() ?: 0)
            if (handlersCacheCounter != combinedRegistrationCounter) {
                handlersCacheLock.write {
                    if (handlersCacheCounter != combinedRegistrationCounter) {
                        handlersCacheCounter = combinedRegistrationCounter
                        handlersCache.clear()
                    }
                }
            }
            handlersCache[type]
        }

        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return cached as List<Handler<TContext, T>>
        }

        val thisHandlers = computeHandlers(type)
        val result = if (parent != null) {
            val parentHandlers = parent.handlers(type)
            when {
                parentHandlers.isEmpty() -> thisHandlers
                thisHandlers.isEmpty() -> parentHandlers
                else -> thisHandlers + parentHandlers
            }
        } else thisHandlers

        handlersCacheLock.write {
            handlersCache[type] = result
        }

        return result
    }

    tailrec fun adjustIdToParents(table: TransformTable<TContext>, id: Int): Int {
        val parent = table.parent ?: return id
        val localId = id + parent.registrationCounter.get()
        return adjustIdToParents(parent, localId)
    }

    suspend fun transform(context: TContext, message: Any): Any {
        val visited = BitSet()

        var value: Any = message
        var handlers = handlers(message::class.java)
        var handlerIndex = 0

        while (handlerIndex < handlers.size) {
            val handler = handlers[handlerIndex++]
            val handlerId = adjustIdToParents(handler.table, handler.localId)

            if (visited.get(handlerId) || !handler.predicate(context, value))
                continue

            val result = handler.transformation(context, value)
            if (result === value)
                continue

            visited.set(handlerId)
            handlerIndex = 0
            if (result::class.java !== value::class.java) {
                handlers = handlers(result::class.java)
            }
            value = result
        }

        return value
    }

    class Handler<TContext : Any, in TValue>(
            val table: TransformTable<TContext>,
            val localId: Int,
            val predicate: TContext.(TValue) -> Boolean,
            val transformation: suspend TContext.(TValue) -> Any) {
        override fun toString() = transformation.toString()
    }

    private fun <T : Any> computeHandlers(type: Class<out T>): List<Handler<TContext, T>> {
        val result = ArrayList<Handler<TContext, T>>(2)
        val superTypes = type.getAllSuperTypes()

        registrationsLock.read {
            for (superType in superTypes) {
                val handlers = registrations[superType]
                @Suppress("UNCHECKED_CAST")
                when (handlers) {
                    is Handler<*, *> -> result.add(handlers as Handler<TContext, T>)
                    is List<*> -> {
                        if ((handlers as List<Handler<TContext, T>>).isNotEmpty())
                            result.addAll(handlers)
                    }
                }
            }
        }

        return if (result.isEmpty()) emptyList() else result
    }

    companion object {
        private val classHierarchyCacheLock = ReentrantReadWriteLock()
        private val classHierarchyCache = HashMap<Class<*>, Array<Class<*>>>()

        private fun Class<*>.getAllSuperTypes(): Array<Class<*>> = classHierarchyCacheLock.read {
            classHierarchyCache[this] ?: classHierarchyCacheLock.write {
                computeSuperTypes(this).also { classHierarchyCache[this] = it }
            }
        }

        private fun computeSuperTypes(type: Class<*>) = type.findAllSupertypes().asReversed().toTypedArray()
    }
}
