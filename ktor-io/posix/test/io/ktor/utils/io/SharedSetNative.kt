// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.locks.*
import kotlin.math.*

internal actual class SharedSet<T> {
    private var _size: Int by shared(0)
    private var content by shared(SharedVector<SharedVector<T>>())
    private val loadFactor: Float get() = size.toFloat() / content.size
    private val lock = SynchronizedObject()

    init {
        initBuckets(8)
    }

    actual val size: Int get() = _size

    actual fun add(element: T): Boolean = synchronized(lock) {
        val bucket = findBucket(element)

        if (bucket.find(element) >= 0) {
            return false
        }

        bucket.push(element)
        _size++

        if (loadFactor > 0.75) {
            doubleSize()
        }

        return true
    }

    actual fun remove(element: T): Boolean = synchronized(lock) {
        val bucket = findBucket(element)
        val result = bucket.remove(element)

        if (result) {
            _size--
        }

        return result
    }

    private fun doubleSize() {
        val old = content
        initBuckets(content.size * 2)
        _size = 0

        for (bucketId in 0 until old.size) {
            val bucket = old[bucketId]
            for (itemId in 0 until bucket.size) {
                add(bucket[itemId])
            }
        }
    }

    private fun initBuckets(count: Int) {
        val newContent = SharedVector<SharedVector<T>>()
        repeat(count) {
            newContent.push(SharedVector())
        }

        content = newContent
    }

    private fun findBucket(element: T): SharedVector<T> = content[(element.hashCode().absoluteValue) % content.size]
}

