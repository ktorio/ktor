/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.config.*
import io.ktor.util.*

/**
 * Represents a simple user's principal identified by [name]
 * @property name of user
 */
public data class UserIdPrincipal(val name: String) : Principal

/**
 * Represents a simple user [name] and [password] credential pair
 * @property name
 * @property password
 */
public data class UserPasswordCredential(val name: String, val password: String) : Credential

/**
 * Simple in-memory table that keeps user names and password hashes
 * @property digester a hash function to compute password digest
 * @property table of user names and hashed passwords
 */
public class UserHashedTableAuth(public val digester: (String) -> ByteArray, public val table: Map<String, ByteArray>) {
    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

    /**
     * Authenticate user by [credential] and return an instance of [UserIdPrincipal]
     * if the [credential] pair is valid
     */
    public fun authenticate(credential: UserPasswordCredential): UserIdPrincipal? {
        val userPasswordHash = table[credential.name]
        if (userPasswordHash != null && digester(credential.password) contentEquals userPasswordHash) {
            return UserIdPrincipal(credential.name)
        }

        return null
    }
}

private fun ApplicationConfig.parseUsers(name: String = "users") =
    configList(name)
        .map { it.property("name").getString() to it.property("hash").getString().decodeBase64Bytes() }
        .toMap()
