/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

import io.ktor.utils.io.pool.*

internal expect class CharArrayBuilder(pool: ObjectPool<CharArray> = CharArrayPool) : CharSequence, Appendable {

    override var length: Int
        private set

    override fun get(index: Int): Char

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence

    override fun append(value: Char): Appendable

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable

    override fun append(value: CharSequence?): Appendable

    fun release()
}
