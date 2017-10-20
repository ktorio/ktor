package io.ktor.samples.kweet.dao

import io.ktor.samples.kweet.model.*
import org.ehcache.*
import org.ehcache.config.*
import org.ehcache.config.persistence.*
import org.ehcache.config.units.*
import org.joda.time.*
import java.io.*

class DAOFacadeCache(val delegate: DAOFacade, val storagePath: File) : DAOFacade {
    val cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
            .with(CacheManagerPersistenceConfiguration(storagePath))
            .withCache("kweetsCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder<Int, Kweet>()
                            .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
                                    .heap(1000, EntryUnit.ENTRIES)
                                    .offheap(10, MemoryUnit.MB)
                                    .disk(100, MemoryUnit.MB, true)
                            )
                            .buildConfig(Int::class.javaObjectType, Kweet::class.java))
            .withCache("usersCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder<String, User>()
                            .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
                                    .heap(1000, EntryUnit.ENTRIES)
                                    .offheap(10, MemoryUnit.MB)
                                    .disk(100, MemoryUnit.MB, true)
                            )
                            .buildConfig(String::class.java, User::class.java))
            .build(true)

    val kweetsCache = cacheManager.getCache("kweetsCache", Int::class.javaObjectType, Kweet::class.java)

    val usersCache = cacheManager.getCache("usersCache", String::class.java, User::class.java)

    override fun init() {
        delegate.init()
    }

    override fun countReplies(id: Int): Int {
        return delegate.countReplies(id)
    }

    override fun createKweet(user: String, text: String, replyTo: Int?, date: DateTime): Int {
        val id = delegate.createKweet(user, text, replyTo)
        val kweet = Kweet(id, user, text, date, replyTo)
        kweetsCache.put(id, kweet)
        return id
    }

    override fun deleteKweet(id: Int) {
        delegate.deleteKweet(id)
        kweetsCache.remove(id)
    }

    override fun getKweet(id: Int): Kweet {
        val cached = kweetsCache.get(id)
        if (cached != null) {
            return cached
        }

        val kweet = delegate.getKweet(id)
        kweetsCache.put(id, kweet)

        return kweet
    }

    override fun userKweets(userId: String): List<Int> {
        return delegate.userKweets(userId)
    }

    override fun user(userId: String, hash: String?): User? {
        val cached = usersCache.get(userId)
        val user = if (cached == null) {
            val dbUser = delegate.user(userId)
            if (dbUser != null) {
                usersCache.put(userId, dbUser)
            }
            dbUser
        } else {
            cached
        }

        return when {
            user == null -> null
            hash == null -> user
            user.passwordHash == hash -> user
            else -> null
        }
    }

    override fun userByEmail(email: String): User? {
        return delegate.userByEmail(email)
    }

    override fun createUser(user: User) {
        if (usersCache.get(user.userId) != null) {
            throw IllegalStateException("User already exist")
        }

        delegate.createUser(user)
        usersCache.put(user.userId, user)
    }

    override fun top(count: Int): List<Int> {
        return delegate.top(count)
    }

    override fun latest(count: Int): List<Int> {
        return delegate.latest(count)
    }

    override fun close() {
        try {
            delegate.close()
        } finally {
            cacheManager.close()
        }
    }
}
