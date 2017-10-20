package io.ktor.samples.kweet.dao

import org.jetbrains.exposed.sql.*

object Users : Table() {
    val id = varchar("id", 20).primaryKey()
    val email = varchar("email", 128).uniqueIndex()
    val displayName = varchar("display_name", 256)
    val passwordHash = varchar("password_hash", 64)
}
