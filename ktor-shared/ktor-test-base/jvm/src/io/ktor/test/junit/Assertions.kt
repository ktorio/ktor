/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.junit

import java.io.*
import kotlin.test.*

/**
 * Convenience function for asserting on all elements of a collection.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.junit.assertAll)
 */
fun <T> assertAll(collection: Iterable<T>, message: String? = null, predicate: (T) -> Boolean) {
    org.junit.jupiter.api.assertAll(
        collection.map { item ->
            {
                org.junit.jupiter.api.Assertions.assertTrue(predicate(item), message)
            }
        }
    )
}

/**
 * Convenience function for asserting on all elements of a collection.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.test.junit.assertAll)
 */
fun <T> assertAll(collection: Iterable<T>, assertion: (T) -> Unit) {
    org.junit.jupiter.api.assertAll(
        collection.map { item ->
            {
                assertion(item)
            }
        }
    )
}

inline fun <reified T : Any> assertSerializable(obj: T, checkEquality: Boolean = true): T {
    val encoded = ByteArrayOutputStream().also {
        ObjectOutputStream(it).writeObject(obj)
    }.toByteArray()
    val decoded = ObjectInputStream(encoded.inputStream()).readObject() as T
    if (checkEquality) {
        assertEquals(obj, decoded, "deserialized object must be equal to original object")
    }
    return decoded
}
