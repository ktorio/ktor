package io.ktor.sessions

import io.ktor.experimental.client.redis.*
import io.ktor.util.*
import java.util.*

class RedisSessionStorage(val redis: Redis, val prefix: String = "session_", val ttlSeconds: Int = 3600) :
    SimplifiedSessionStorage() {
    private fun buildKey(id: String) = "$prefix$id"

    override suspend fun read(id: String): ByteArray? {
        val key = buildKey(id)
        return redis.get(key)?.let { hex(it) }?.apply {
            redis.expire(key, ttlSeconds) // refresh
        }
    }

    override suspend fun write(id: String, data: ByteArray?) {
        val key = buildKey(id)
        if (data == null) {
            redis.del(buildKey(id))
        } else {
            redis.set(key, hex(data))
            redis.expire(key, ttlSeconds)
        }
    }
}

inline fun <reified T> CurrentSession.getOrNull(): T? = try {
    get(findName(T::class)) as T?
} catch (e: NoSuchElementException) {
    null
}