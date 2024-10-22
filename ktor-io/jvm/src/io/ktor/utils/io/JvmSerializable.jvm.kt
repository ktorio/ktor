/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import java.io.*

public actual typealias JvmSerializable = Serializable

@Suppress("UNCHECKED_CAST")
public actual fun <T : Any> JvmSerializerReplacement(serializer: JvmSerializer<T>, value: T): Any =
    DefaultJvmSerializerReplacement(serializer, value)

@PublishedApi // IMPORTANT: changing the class name would result in serialization incompatibility
internal class DefaultJvmSerializerReplacement<T : Any>(
    private var serializer: JvmSerializer<T>?,
    private var value: T?
) : Externalizable {
    constructor() : this(null, null)

    override fun writeExternal(out: ObjectOutput) {
        out.writeObject(serializer)
        out.writeObject(serializer!!.jvmSerialize(value!!))
    }

    @Suppress("UNCHECKED_CAST")
    override fun readExternal(`in`: ObjectInput) {
        serializer = `in`.readObject() as JvmSerializer<T>
        value = serializer!!.jvmDeserialize(`in`.readObject() as ByteArray)
    }

    private fun readResolve(): Any =
        value!!

    companion object {
        private const val serialVersionUID: Long = 0L
    }
}
