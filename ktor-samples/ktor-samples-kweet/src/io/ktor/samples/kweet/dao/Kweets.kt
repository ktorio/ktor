package io.ktor.samples.kweet.dao

import org.jetbrains.exposed.sql.*

object Kweets : Table() {
    val id = integer("id").primaryKey().autoIncrement()
    val user = varchar("user_id", 20).index()
    val date = datetime("date")
    val replyTo = integer("reply_to").index().nullable()
    val directReplyTo = integer("direct_reply_to").index().nullable()
    val text = varchar("text", 1024)
}
