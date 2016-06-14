package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.*
import kotlin.reflect.*

class TransformTable<C : Any> {
    private val root = Entry<C, Any>(Any::class.java, null)
    private val cache = HashMap<Class<*>, MutableList<Entry<C, *>>>(1)

    init {
        cache[Any::class.java] = mutableListOf<Entry<C, *>>(root)
    }

    inline fun <reified T : Any> register(noinline predicate: C.(T) -> Boolean = { true }, noinline handler: C.(T) -> Any) {
        register(T::class, predicate, handler)
    }

    fun <T : Any> register(type: KClass<T>, predicate: C.(T) -> Boolean, handler: C.(T) -> Any) {
        @Suppress("UNCHECKED_CAST")
        registerImpl(type.java, root as Entry<C, T>, Handler(predicate, handler))
    }

    inline fun <reified T : Any> handlers() = handlers(T::class.java)

    fun <T : Any> handlers(type: Class<T>) =
            collectCached(type) ?:
                    ArrayList<Handler<C, T>>().apply {
                        collectImpl(type, mutableListOf<Entry<C, *>>(root), this)
                    }.asReversed()

    fun copy(): TransformTable<C> {
        val newInstance = TransformTable<C>()
        newInstance.root.leafs.addAll(root.leafs.map { it.copy() })
        newInstance.root.leafs.forEach { newInstance.cacheChildren(it) }

        return newInstance
    }

    private fun cacheChildren(node: Entry<C, *>) {
        dfs(node) { it.leafs }.filter { !it.type.isInterface }.forEach {
            cache.getOrPut(it.type) { ArrayList() }.add(it)
        }
    }

    private fun <T : Any> registerImpl(type: Class<T>, node: Entry<C, T>, handler: Handler<C, T>): Int {
        if (node.type === type) {
            node.handlers.add(handler)
            return 1
        } else if (node.type.isAssignableFrom(type)) {
            val installed = node.leafs.map { registerImpl(type, it, handler) }.sum()
            if (installed == 0) {
                val entry = insertEntry(type, node)
                if (!type.isInterface) {
                    cache.getOrPut(type) { ArrayList() }.add(entry)
                }
                return registerImpl(type, entry, handler)
            }

            return installed
        }

        return 0
    }

    private fun <T : Any> insertEntry(type: Class<T>, parent: Entry<C, T>): Entry<C, T> {
        val newTypeEntry = Entry(type, parent)
        val parentType = parent.type
        val superTypes = type.superTypes().takeLastWhile { it !== parentType }

        for (leaf in parent.leafs) {
            val leafSuperTypes = leaf.type.superTypes().takeLastWhile { it !== parentType }.dropWhile { it !== type }
            var common: Class<*>? = null
            for (idx in 0 .. Math.min(superTypes.size, leafSuperTypes.size) - 1) {
                if (superTypes[idx] === leafSuperTypes[idx]) {
                    common = superTypes[idx]
                } else {
                    break
                }
            }

            if (common == type) {
                parent.leafs.remove(leaf)
                parent.leafs.add(newTypeEntry)
                newTypeEntry.leafs.add(leaf)
                leaf.parent = newTypeEntry

                return newTypeEntry
            } else if (common != null) {
                @Suppress("UNCHECKED_CAST")
                val nodeForCommon = Entry(common as Class<T>, parent)
                parent.leafs.remove(leaf)
                nodeForCommon.leafs.add(leaf)
                leaf.parent = nodeForCommon

                if (!common.isInterface) {
                    cache.getOrPut(common) { ArrayList() }.add(nodeForCommon)
                }

                newTypeEntry.parent = nodeForCommon
                newTypeEntry.leafs.add(leaf)
                return newTypeEntry
            }
        }

        newTypeEntry.leafs.addAll(findSubTypes(parent, type).mapNotNull { it.castOrNull(type)?.copy() })
        newTypeEntry.leafs.forEach { cacheChildren(it) }
        parent.leafs.add(newTypeEntry)

        return newTypeEntry
    }

    private fun findSubTypes(node: Entry<C, *>, type: Class<*>): List<Entry<C, *>> = ArrayList<Entry<C, *>>().apply {
        findSubTypes(mutableListOf(node), type, this)
    }.distinctBy { it.type }

    private fun findSubTypes(nodes: MutableList<Entry<C, *>>, type: Class<*>, good: MutableList<Entry<C, *>>) {
        if (nodes.isEmpty()) return
        val node = nodes.removeLast()

        if (type.isAssignableFrom(node.type)) {
            good.add(node)
        } else {
            nodes.addAll(node.leafs)
        }

        findSubTypes(nodes, type, good)
    }

    tailrec
    private fun <T : Any> collectImpl(type: Class<T>, nodes: MutableList<Entry<C, *>>, result: ArrayList<Handler<C, T>>) {
        val current = nodes.lookup(type) ?: return

        result.addAll(current.handlers)
        nodes.addAll(current.leafs)

        collectImpl(type, nodes, result)
    }

    private fun <T : Any> collectCached(type: Class<T>): List<Handler<C, T>>? {
        val exactNodes = cache[type]?.mapNotNull { it.castOrNull(type) }
        if (exactNodes == null || exactNodes.isEmpty()) {
            return null
        }

        val collected = ArrayList<Handler<C, T>>()

        for (root in exactNodes) {
            collected.addAll(root.handlers)
            var current = root.parent
            while (current != null) {
                collected.addAll(current.handlers)
                current = current.parent
            }
        }

        return collected
    }

    tailrec
    private fun <T : Any> MutableList<Entry<C, *>>.lookup(type: Class<T>): Entry<C, T>? {
        if (isEmpty()) return null

        return removeAt(lastIndex).castOrNull(type) ?: lookup(type)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> Entry<C, *>.castOrNull(type: Class<T>) = if (this.type.isAssignableFrom(type)) this as Entry<C, T> else null

    private class Entry<C : Any, T : Any>(val type: Class<T>, var parent: Entry<C, in T>?) {
        val handlers = ArrayList<Handler<C, T>>()
        val leafs = ArrayList<Entry<C, T>>()

        override fun toString() = "Entry(${type.name})"
    }

    private fun <T : Any> Entry<C, T>.copy(): Entry<C, T> {
        val newInstance = Entry(type, parent)
        newInstance.handlers.addAll(handlers)
        val newLeafs = leafs.map { it.copy() }
        newLeafs.forEach { it.parent = newInstance }
        newInstance.leafs.addAll(newLeafs)

        return newInstance
    }

    class Handler<in C : Any, in T> internal constructor(val predicate: C.(T) -> Boolean, val handler: C.(T) -> Any) {
        override fun toString() = handler.toString()
    }

    private fun <T> Class<T>.superTypes() = dfs(this)
}

fun <C : Any> TransformTable<C>.transform(ctx: C, obj: Any) = transformImpl(ctx, obj)

tailrec
private fun <C : Any, T : Any> TransformTable<C>.transformImpl(ctx: C, obj: T, handlers: List<TransformTable.Handler<C, T>> = handlers(obj.javaClass), visited: MutableSet<TransformTable.Handler<C, *>> = HashSet()): Any {
    for (handler in handlers) {
        if (handler !in visited && handler.predicate(ctx, obj)) {
            val result = handler.handler(ctx, obj)

            if (result === obj) {
                continue
            }

            visited.add(handler)
            if (result.javaClass === obj.javaClass) {
                @Suppress("UNCHECKED_CAST")
                return transformImpl(ctx, result as T, handlers, visited)
            } else {
                return transformImpl(ctx, result, handlers(result.javaClass), visited)
            }
        }
    }

    return obj
}

internal fun PipelineContext<ResponsePipelineState>.transform() {
    val machine = PipelineMachine()
    val phase = PipelinePhase("phase")
    val pipeline = Pipeline<ResponsePipelineState>(phase)
    val state = TransformationState()

    call.attributes.put(TransformationState.Key, state)
    pipeline.intercept(phase) {
        onSuccess {
            this@transform.continuePipeline()
        }

        transformStage(machine, state)
    }

    machine.execute(subject, pipeline)
}

fun PipelineContext<ResponsePipelineState>.proceed(value: Any): Nothing {
    if (subject.obj !== value) {
        subject.obj = value
        call.attributes[TransformationState.Key].markLastHandlerVisited()
    }

    proceed()
}

tailrec
private fun PipelineContext<ResponsePipelineState>.transformStage(machine: PipelineMachine, state: TransformationState) {
    if (state.completed) {
        return
    }

    machine.appendDelayed(subject, listOf({ p ->
        @Suppress("NON_TAIL_RECURSIVE_CALL")
        transformStage(machine, state)
    }))

    val obj = subject.obj
    val visited = state.visited
    val handlers = subject.call.transform.handlers(obj.javaClass).filter { it !in visited }

    if (handlers.isNotEmpty()) {
        for (handler in handlers) {
            if (handler.predicate(this, obj)) {
                state.lastHandler = handler
                val nextResult = handler.handler(this, obj)
                state.lastHandler = null

                if (nextResult !== obj) {
                    subject.obj = nextResult
                    visited.add(handler)
                    return transformStage(machine, state)
                }
            }
        }
    }

    state.completed = true
}

private class TransformationState {
    val visited: MutableSet<TransformTable.Handler<PipelineContext<ResponsePipelineState>, *>> = hashSetOf()
    var completed: Boolean = false
    var lastHandler: TransformTable.Handler<PipelineContext<ResponsePipelineState>, *>? = null

    fun markLastHandlerVisited() {
        val handler = lastHandler
        lastHandler = null

        if (handler != null) {
            visited.add(handler)
        }
    }

    companion object {
        val Key = AttributeKey<TransformationState>("TransformationState.key")
    }
}