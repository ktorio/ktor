package io.ktor.samples.kweet.dao

import io.ktor.samples.kweet.model.*
import org.jetbrains.exposed.sql.*
import org.joda.time.*
import java.io.*

interface DAOFacade : Closeable {
    fun init()
    fun countReplies(id: Int): Int
    fun createKweet(user: String, text: String, replyTo: Int? = null, date: DateTime = DateTime.now()): Int
    fun deleteKweet(id: Int)
    fun getKweet(id: Int): Kweet
    fun userKweets(userId: String): List<Int>
    fun user(userId: String, hash: String? = null): User?
    fun userByEmail(email: String): User?
    fun createUser(user: User)
    fun top(count: Int = 10): List<Int>
    fun latest(count: Int = 10): List<Int>
}


class DAOFacadeDatabase(val db: Database = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")): DAOFacade {
    constructor(dir: File) : this(Database.connect("jdbc:h2:file:${dir.canonicalFile.absolutePath}", driver = "org.h2.Driver"))

    override fun init() {
        db.transaction {
            create(Users, Kweets)
        }
    }

    override fun countReplies(id: Int): Int {
        return db.transaction {
            Kweets.slice(Kweets.id.count()).select {
                Kweets.replyTo.eq(id)
            }.single()[Kweets.id.count()]
        }
    }

    override fun createKweet(user: String, text: String, replyTo: Int?, date: DateTime): Int {
        return db.transaction {
            Kweets.insert {
                it[Kweets.user] = user
                it[Kweets.date] = date
                it[Kweets.replyTo] = replyTo
                it[Kweets.text] = text
            }.generatedKey ?: throw IllegalStateException("No generated key returned")
        }
    }

    override fun deleteKweet(id: Int) {
        db.transaction {
            Kweets.deleteWhere { Kweets.id.eq(id) }
        }
    }

    override fun getKweet(id: Int) = db.transaction {
        val row = Kweets.select { Kweets.id.eq(id) }.single()
        Kweet(id, row[Kweets.user], row[Kweets.text], row[Kweets.date], row[Kweets.replyTo])
    }

    override fun userKweets(userId: String) = db.transaction {
        Kweets.slice(Kweets.id).select { Kweets.user.eq(userId) }.orderBy(Kweets.date, false).limit(100).map { it[Kweets.id] }
    }

    override fun user(userId: String, hash: String?) = db.transaction {
        Users.select { Users.id.eq(userId) }
                .mapNotNull {
                    if (hash == null || it[Users.passwordHash] == hash) {
                        User(userId, it[Users.email], it[Users.displayName], it[Users.passwordHash])
                    } else {
                        null
                    }
                }
                .singleOrNull()
    }

    override fun userByEmail(email: String) = db.transaction {
        Users.select { Users.email.eq(email) }
                .map { User(it[Users.id], email, it[Users.displayName], it[Users.passwordHash]) }.singleOrNull()
    }

    override fun createUser(user: User) = db.transaction {
        Users.insert {
            it[id] = user.userId
            it[displayName] = user.displayName
            it[email] = user.email
            it[passwordHash] = user.passwordHash
        }
        Unit
    }

    override fun top(count: Int): List<Int> = db.transaction {
        // note: in a real application you shouldn't do it like this
        //   as it may cause database outages on big data
        //   so this implementation is just for demo purposes

        val k2 = Kweets.alias("k2")
        Kweets.join(k2, JoinType.LEFT, Kweets.id, k2[Kweets.replyTo])
                .slice(Kweets.id, k2[Kweets.id].count())
                .selectAll()
                .groupBy(Kweets.id)
                .orderBy(k2[Kweets.id].count(), isAsc = false)
//                .having { k2[Kweets.id].count().greater(0) }
                .limit(count)
                .map { it[Kweets.id] }
    }

    override fun latest(count: Int): List<Int> = db.transaction {
        var attempt = 0
        var allCount: Int? = null

        for (minutes in generateSequence(2) { it * it }) {
            attempt++

            val dt = DateTime.now().minusMinutes(minutes)

            val all = Kweets.slice(Kweets.id)
                    .select { Kweets.date.greater(dt) }
                    .orderBy(Kweets.date, false)
                    .limit(count)
                    .map { it[Kweets.id] }

            if (all.size >= count) {
                return@transaction all
            }
            if (attempt > 10 && allCount == null) {
                allCount = Kweets.slice(Kweets.id.count()).selectAll().count()
                if (allCount <= count) {
                    return@transaction Kweets.slice(Kweets.id).selectAll().map { it[Kweets.id] }
                }
            }
        }

        emptyList()
    }

    override fun close() {
    }
}
