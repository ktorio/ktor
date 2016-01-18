package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.interception.*
import java.lang.ref.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

interface Cache<K : Any, V : Any> {
    operator fun get(key: K): V
    fun peek(key: K): V?
    fun invalidate(key: K): V?
    fun invalidate(key: K, value: V): Boolean
    fun invalidateAll()

    fun intercept(block: (K, (K) -> V) -> V)
}

internal interface CacheReference<K> {
    val key: K
}

internal class BaseCache<K : Any, V : Any>(val calc: (K) -> V) : Cache<K, V> {

    private val container = ConcurrentHashMap<K, Lazy<V>>()
    private val supplier = Interceptable1(calc)

    override fun get(key: K): V =
            container.computeIfAbsent(key) { lazy(LazyThreadSafetyMode.SYNCHRONIZED) { supplier.call(key) } }.value

    override fun peek(key: K): V? = container[key]?.let { if (it.isInitialized()) it.value else null }

    override fun invalidate(key: K): V? = container.remove(key)?.let { l -> if (l.isInitialized()) l.value else null }
    override fun invalidate(key: K, value: V) = container[key]?.let { l -> l.isInitialized() && l.value == value && container.remove(key, l) } ?: false
    override fun invalidateAll() {
        container.clear()
    }

    override fun intercept(block: (K, (K) -> V) -> V) {
        invalidateAll()
        supplier.intercept(block)
    }
}

internal open class ReferenceCache<K : Any, V : Any, R>(val calc: (K) -> V, val wrapFunction: (K, V, ReferenceQueue<V>) -> R) : Cache<K, V> where R : Reference<V>, R : CacheReference<K> {
    private val queue = ReferenceQueue<V>()
    private val supplier = Interceptable1(calc)
    private val container = BaseCache { key: K -> forkThreadIfNeeded(); wrapFunction(key, supplier.call(key), queue) }
    private val workerThread by lazy { Thread(ReferenceWorker(container, queue)).apply { isDaemon = true; start() } }

    override fun get(key: K): V {
        val ref = container[key]
        val value = ref.get()

        if (value == null) {
            if (container.invalidate(key, ref)) {
                ref.enqueue()
            }
            return get(key)
        }

        return value
    }

    override fun peek(key: K): V? = container.peek(key)?.get()

    override fun invalidate(key: K): V? = container.invalidate(key)?.get()
    override fun invalidate(key: K, value: V): Boolean {
        val ref = container.peek(key)

        if (ref?.get() == value) {
            ref!!.enqueue()
            return container.invalidate(key, ref)
        }

        return false
    }

    override fun invalidateAll() {
        container.invalidateAll()
    }

    override fun intercept(block: (K, (K) -> V) -> V) {
        invalidateAll()
        supplier.intercept(block)
    }

    private fun forkThreadIfNeeded() {
        if (!workerThread.isAlive) {
            throw IllegalStateException("Daemon thread is already dead")
        }
    }
}

private class ReferenceWorker<K : Any, R : CacheReference<K>>(owner: Cache<K, R>, val queue: ReferenceQueue<*>) : Runnable {
    private val owner = WeakReference(owner)

    override fun run() {
        do {
            val ref = queue.remove(60000)
            if (ref is CacheReference<*>) {
                @Suppress("UNCHECKED_CAST")
                val cast = ref as R
                val currentOwner = owner.get() ?: break

                currentOwner.invalidate(cast.key, cast)
            }
        } while (!Thread.interrupted() && owner.get() != null)
    }
}

internal class CacheSoftReference<K, V>(override val key: K, value: V, queue: ReferenceQueue<V>) : SoftReference<V>(value, queue), CacheReference<K>
internal class CacheWeakReference<K, V>(override val key: K, value: V, queue: ReferenceQueue<V>) : WeakReference<V>(value, queue), CacheReference<K>

internal class SoftReferenceCache<K : Any, V : Any>(calc: (K) -> V) : ReferenceCache<K, V, CacheSoftReference<K, V>>(calc, { k, v, q -> CacheSoftReference(k, v, q) })
internal class WeakReferenceCache<K : Any, V : Any>(calc: (K) -> V) : ReferenceCache<K, V, CacheWeakReference<K, V>>(calc, { k, v, q -> CacheWeakReference(k, v, q) })

internal class BaseTimeoutCache<K : Any, V : Any>(val timeoutValue: Long, val touchOnGet: Boolean, val touchOnCreate: Boolean, val delegate: Cache<K, V>) : Cache<K, V> {

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    private val items = PullableLinkedList<KeyState<K>>()
    private val map = WeakHashMap<K, KeyState<K>>()

    private val workerThread by lazy { Thread(TimeoutWorker(this, lock, cond, items)).apply { isDaemon = true; start() } }

    init {
        if (touchOnCreate) {
            delegate.intercept { key, next ->
                pull(key)
                next(key)
            }
        }
    }

    override fun get(key: K): V {
        if (touchOnGet) {
            pull(key)
        }
        return delegate[key]
    }

    override fun peek(key: K): V? {
        if (touchOnGet) {
            pull(key, create = false)
        }
        return delegate.peek(key)
    }

    override fun invalidate(key: K): V? {
        remove(key)
        return delegate.invalidate(key)
    }

    override fun invalidate(key: K, value: V): Boolean {
        if (delegate.invalidate(key, value)) {
            remove(key)
            return true
        }
        return false
    }

    override fun invalidateAll() {
        delegate.invalidateAll()
        lock.withLock {
            items.clear()
            cond.signalAll()
        }
    }

    override fun intercept(block: (K, (K) -> V) -> V) {
        delegate.intercept(block)
    }

    private fun forkIfNeeded() {
        if (!items.isEmpty() && !workerThread.isAlive) {
            throw IllegalStateException("Daemon thread is already dead")
        }
    }

    private fun pull(key: K, create: Boolean = true) {
        lock.withLock {
            val state = if (create) map.getOrPut(key) { KeyState(key, timeoutValue) } else map[key]
            if (state != null) {
                state.touch()
                items.pull(state)
                cond.signalAll()
            }
        }
        forkIfNeeded()
    }

    private fun remove(key: K) {
        lock.withLock {
            map.remove(key)?.let {
                items.remove(it)
                cond.signalAll()
            }
        }
    }
}

private class KeyState<K>(key: K, val timeout: Long) : ListElement<KeyState<K>>() {
    val key = WeakReference(key)
    var lastAccess = System.currentTimeMillis()

    fun touch() {
        lastAccess = System.currentTimeMillis()
    }

    fun timeToWait() = Math.max(0L, lastAccess + timeout - System.currentTimeMillis())
}

private class TimeoutWorker<K : Any>(owner: BaseTimeoutCache<K, *>, val lock: ReentrantLock, val cond: Condition, val items : PullableLinkedList<KeyState<K>>) : Runnable {
    private val owner = WeakReference(owner)

    override fun run() {
        do {
            lock.withLock {
                val item = head()
                if (item != null) {
                    val time = item.timeToWait()

                    if (time == 0L) {
                        items.remove(item)
                        val k = item.key.get()
                        if (k != null) {
                            owner.get()?.invalidate(k)
                        }
                    } else {
                        cond.await(time, TimeUnit.MILLISECONDS)
                    }
                }
            }
        } while (!Thread.interrupted() && owner.get() != null)
    }

    private fun head() =
            lock.withLock {
                while (items.isEmpty() && owner.get() != null) {
                    cond.await(60, TimeUnit.SECONDS)
                }

                if (owner.get() == null) null else items.head()
            }
}

private abstract class ListElement<E: ListElement<E>> {
    var next: E? = null
    var prev: E? = null
}

private class PullableLinkedList<E: ListElement<E>> {
    private var head: E? = null
    private var tail: E? = null

    fun isEmpty() = head == null
    fun take(): E = head().apply { remove(this) }
    fun head(): E = head ?: throw NoSuchElementException()

    fun add(element: E) {
        require(element.next == null)
        require(element.prev == null)

        val oldHead = head
        if (oldHead != null) {
            element.next = oldHead
            oldHead.prev = element
        }
        head = element
        if (tail == null) {
            tail = element
        }
    }

    fun remove(element: E) {
        if (element == head) {
            head = null
        }
        if (element == tail) {
            tail = null
        }

        element.prev?.next = element.next
        element.next = null
        element.prev = null
    }

    fun clear() {
        head = null
        tail = null
    }

    fun pull(element: E) {
        if (element !== head) {
            remove(element)
            add(element)
        }
    }
}