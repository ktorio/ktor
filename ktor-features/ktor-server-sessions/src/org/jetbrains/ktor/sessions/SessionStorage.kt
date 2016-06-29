package org.jetbrains.ktor.sessions

import org.slf4j.*
import java.io.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

interface SessionStorage {
    fun save(id: String, contentProvider: (OutputStream) -> Unit): Future<Unit>
    fun <R> read(id: String, consumer: (InputStream) -> R): Future<R>
    fun invalidate(id: String): Future<Unit>
}

class CacheStorage(val delegate: SessionStorage, val idleTimeout: Long) : SessionStorage {
    private val cache = BaseTimeoutCache(idleTimeout, true, SoftReferenceCache<String, ByteArray> { id -> delegate.read(id) { input -> input.readBytes() }.get() })
    private val exec = Executors.newCachedThreadPool()

    override fun <R> read(id: String, consumer: (InputStream) -> R): Future<R> =
            exec.submit(Callable { consumer(cache[id].inputStream()) })

    override fun save(id: String, contentProvider: (OutputStream) -> Unit): Future<Unit> {
        cache.invalidate(id)
        return delegate.save(id, contentProvider)
    }

    override fun invalidate(id: String): Future<Unit> {
        cache.invalidate(id)
        return delegate.invalidate(id)
    }
}

private class InMemorySessionStorage : SessionStorage {
    private val sessions = ConcurrentHashMap<String, ByteArray>()

    override fun save(id: String, contentProvider: (OutputStream) -> Unit): Future<Unit> {
        val baos = ByteArrayOutputStream()
        contentProvider(baos)
        sessions[id] = baos.toByteArray()
        return CompletableFuture.completedFuture(Unit)
    }

    override fun <R> read(id: String, consumer: (InputStream) -> R): Future<R> = CompletableFuture<R>().apply {
        sessions[id]?.let { bytes -> complete(consumer(bytes.inputStream())) } ?: throw IllegalArgumentException("Session $id not found")
    }

    override fun invalidate(id: String): Future<Unit> {
        sessions.remove(id)
        return CompletableFuture.completedFuture(Unit)
    }
}

fun directorySessionStorage(rootDir: File, cached: Boolean = true): SessionStorage = when (cached) {
    true -> CacheStorage(DirectoryStorage(rootDir), 60000)
    false -> DirectoryStorage(rootDir)
}

fun inMemorySessionStorage(): SessionStorage = InMemorySessionStorage()

private class DirectoryStorage(val dir: File) : SessionStorage, Closeable {
    private val poisonTask = IOTasksGroup("")
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()
    private val tasks = HashMap<String, IOTasksGroup>()
    private val thread: Thread
    private var currentId = AtomicReference("")

    init {
        dir.mkdirsOrFail()

        thread = thread(name = "directory-storage-handler") {
            do {
                val task = takeTask()
                if (task === poisonTask) {
                    break
                }
                val file = fileOf(task.id)

                task.writeTask?.let { writer ->
                    try {
                        file.parentFile?.mkdirsOrFail()
                        file.outputStream().buffered().use(writer.writerFunction)
                        writer.future.complete(Unit)
                    } catch (t: Throwable) {
                        writer.future.completeExceptionally(t)
                        LoggerFactory.getLogger(DirectoryStorage::class.java).error("Failed to write to $file", t)
                    }
                }

                task.readTasks.forEach { reader ->
                    try {
                        file.inputStream().use {
                            reader.readFunction(it)
                        }
                    } catch (t: Throwable) {
                        reader.future.completeExceptionally(t)
                        LoggerFactory.getLogger(DirectoryStorage::class.java).error("Failed to read from $file", t)
                    }
                }
                currentId.set("")

                if (task.invalidateTasks.isNotEmpty()) {
                    try {
                        file.delete()
                        file.parentFile?.deleteParentsWhileEmpty(dir)
                        task.invalidateTasks.forEach {
                            it.future.complete(Unit)
                        }
                    } catch (t: Throwable) {
                        task.invalidateTasks.forEach {
                            it.future.complete(Unit)
                        }
                    }
                }
            } while (!Thread.interrupted())
        }
    }

    override fun close() {
        lock.withLock {
            tasks[""] = poisonTask
            cond.signalAll()
        }
        thread.join()
    }

    private fun takeTask() = lock.withLock {
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        var task: IOTasksGroup? = null

        do {
            currentId.set("")
            val k = tasks.keys.firstOrNull()
            if (k == null) {
                cond.await()
            } else {
                task = tasks.remove(k)
                currentId.set(task!!.id)
                break
            }
        } while (true)

        task!!
    }

    override fun save(id: String, contentProvider: (OutputStream) -> Unit): Future<Unit> {
        requireId(id)
        val future = CompletableFuture<Unit>()

        lock.withLock {
            val task = tasks.getOrPut(id) { IOTasksGroup(id) }

            task.writeTask?.future?.cancel(false)
            task.writeTask = WriteTask(contentProvider, future)

            task.invalidateTasks.forEach {
                it.future.cancel(false)
            }
            task.invalidateTasks.clear()
            cond.signalAll()
        }

        return future
    }

    override fun <R> read(id: String, consumer: (InputStream) -> R): Future<R> {
        requireId(id)
        val f = CompletableFuture<R>()

        lock.withLock {
            var task = tasks[id]
            val file = fileOf(id)

            if (task?.writeTask == null && !file.exists() && currentId.get() != id) {
                throw NoSuchElementException("Session for id $id is missing")
            }
            if (task?.invalidateTasks?.isNotEmpty() == true) {
                throw NoSuchElementException("Session for id $id is invalidated")
            }

            if (task == null) {
                task = IOTasksGroup(id)
                tasks[id] = task
            }

            task.readTasks.add(ReadTask({ inputStream ->
                try {
                    f.complete(consumer(inputStream))
                } catch (e: Throwable) {
                    f.completeExceptionally(e)
                }
            }, f))

            cond.signalAll()
        }

        return f
    }

    override fun invalidate(id: String): Future<Unit> {
        requireId(id)
        val future = CompletableFuture<Unit>()

        lock.withLock {
            val task = tasks.getOrPut(id) { IOTasksGroup(id) }
            task.invalidateTasks.add(InvalidateTask(future))
            cond.signalAll()
        }

        return future
    }

    private fun fileOf(id: String) = File(dir, split(id).joinToString(File.separator, postfix = ".dat"))
    private fun split(id: String) = id.window(2)

    private fun requireId(id: String) {
        if (id.isEmpty()) {
            throw IllegalArgumentException("Session id is empty")
        }
        if (id.indexOfAny(listOf("..", "/", "\\", "!", "?", ">", "<", "\u0000")) != -1) {
            throw IllegalArgumentException("Bad session id $id")
        }
    }
}

private class WriteTask(val writerFunction: (OutputStream) -> Unit, val future: CompletableFuture<Unit>)
private class ReadTask(val readFunction: (InputStream) -> Unit, val future: CompletableFuture<*>)
private class InvalidateTask(val future: CompletableFuture<Unit>)

private class IOTasksGroup(val id: String) {
    val readTasks = ArrayList<ReadTask>()
    var writeTask: WriteTask? = null
    val invalidateTasks = ArrayList<InvalidateTask>()
}

private fun File.mkdirsOrFail() {
    if (!this.mkdirs() && !this.exists()) {
        throw IOException("Couldn't create directory $this")
    }
    if (!this.isDirectory) {
        throw IOException("Path is not a directory: $this")
    }
}

tailrec
private fun File.deleteParentsWhileEmpty(mostTop: File) {
    if (this != mostTop && isDirectory && exists() && list().isNullOrEmpty()) {
        if (!delete() && exists()) {
            throw IOException("Failed to delete dir $this")
        }

        parentFile.deleteParentsWhileEmpty(mostTop)
    }
}

private fun <T> Array<T>?.isNullOrEmpty() = this == null || this.isEmpty()

private fun String.window(size: Int, step: Int = size, dropTrailing: Boolean = false): Sequence<String> =
        if (isEmpty() || (size > length && dropTrailing)) emptySequence()
        else object : Sequence<String> {
            override fun iterator(): Iterator<String> = StringWindowIterator(this@window, size, step, dropTrailing)
        }

private class StringWindowIterator(val string: String, val size: Int, val step: Int, val dropTrailing: Boolean) : AbstractIterator<String>() {
    var currentIndex = 0

    init {
        require(step > 0)
        require(size > 0)
    }

    override fun computeNext() {
        if (currentIndex >= string.length) {
            done()
            return
        }

        val endExclusive = currentIndex + size
        if (endExclusive > string.length && dropTrailing) {
            done()
            return
        }

        setNext(string.substring(currentIndex, Math.min(endExclusive, string.length)))
        currentIndex = endExclusive
    }
}