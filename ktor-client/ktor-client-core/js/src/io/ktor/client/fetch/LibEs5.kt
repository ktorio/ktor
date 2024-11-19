/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fetch

public typealias ArrayBufferLike = Any

public external interface ArrayBufferView {
    public var buffer: ArrayBufferLike
    public var byteLength: Number
    public var byteOffset: Number
}

public external interface ArrayBuffer {
    public var byteLength: Number
    public fun slice(begin: Number, end: Number? = definedExternally): ArrayBuffer

//    companion object : ArrayBufferConstructor by definedExternally
}

public external interface ArrayBufferConstructor {
    public var prototype: ArrayBuffer
    public fun isView(arg: Any): Boolean
}

public external interface Uint8Array {
    public var BYTES_PER_ELEMENT: Number
    public var buffer: ArrayBufferLike
    public var byteLength: Number
    public var byteOffset: Number

    public fun copyWithin(target: Number, start: Number, end: Number? = definedExternally): Uint8Array // this
    public fun every(
        callbackfn: (value: Number, index: Number, array: Uint8Array) -> Any,
        thisArg: Any? = definedExternally
    ): Boolean

    public fun fill(
        value: Number,
        start: Number? = definedExternally,
        end: Number? = definedExternally
    ): Uint8Array // this

    public fun filter(
        callbackfn: (value: Number, index: Number, array: Uint8Array) -> Any,
        thisArg: Any? = definedExternally
    ): Uint8Array

    public fun find(
        predicate: (value: Number, index: Number, obj: Uint8Array) -> Boolean,
        thisArg: Any? = definedExternally
    ): Number?

    public fun findIndex(
        predicate: (value: Number, index: Number, obj: Uint8Array) -> Boolean,
        thisArg: Any? = definedExternally
    ): Number

    public fun forEach(
        callbackfn: (value: Number, index: Number, array: Uint8Array) -> Unit,
        thisArg: Any? = definedExternally
    )

    public fun indexOf(searchElement: Number, fromIndex: Number? = definedExternally): Number
    public fun join(separator: String? = definedExternally): String
    public fun lastIndexOf(searchElement: Number, fromIndex: Number? = definedExternally): Number
    public var length: Number
    public fun map(
        callbackfn: (value: Number, index: Number, array: Uint8Array) -> Number,
        thisArg: Any? = definedExternally
    ): Uint8Array

    public fun reduce(
        callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number
    ): Number
    public fun reduce(
        callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number,
        initialValue: Number
    ): Number
    public fun <U> reduce(
        callbackfn: (previousValue: U, currentValue: Number, currentIndex: Number, array: Uint8Array) -> U,
        initialValue: U
    ): U
    public fun reduceRight(
        callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number
    ): Number
    public fun reduceRight(
        callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number,
        initialValue: Number
    ): Number
    public fun <U> reduceRight(
        callbackfn: (previousValue: U, currentValue: Number, currentIndex: Number, array: Uint8Array) -> U,
        initialValue: U
    ): U
    public fun reverse(): Uint8Array
    public fun set(array: ArrayLike<Number>, offset: Number? = definedExternally)
    public fun slice(start: Number? = definedExternally, end: Number? = definedExternally): Uint8Array
    public fun some(
        callbackfn: (value: Number, index: Number, array: Uint8Array) -> Any,
        thisArg: Any? = definedExternally
    ): Boolean

    public fun sort(compareFn: ((a: Number, b: Number) -> Number)? = definedExternally): Uint8Array // this
    public fun subarray(begin: Number? = definedExternally, end: Number? = definedExternally): Uint8Array
    public fun toLocaleString(): String
    override fun toString(): String

//    companion object : Uint8ArrayConstructor by definedExternally
}

@Suppress("NOTHING_TO_INLINE")
public inline operator fun Uint8Array.get(index: Number): Number? = asDynamic()[index]

@Suppress("NOTHING_TO_INLINE")
public inline operator fun Uint8Array.set(index: Number, value: Number) {
    asDynamic()[index] = value
}

public external interface Uint8ArrayConstructor {
    public var prototype: Uint8Array
    public var BYTES_PER_ELEMENT: Number
    public fun of(vararg items: Number): Uint8Array
    public fun from(arrayLike: ArrayLike<Number>): Uint8Array
    public fun <T> from(
        arrayLike: ArrayLike<T>,
        mapfn: (v: T, k: Number) -> Number,
        thisArg: Any? = definedExternally
    ): Uint8Array
}

public external interface ArrayLike<T> {
    public var length: Number
}

@Suppress("NOTHING_TO_INLINE")
public inline operator fun <T> ArrayLike<T>.get(n: Number): T? = asDynamic()[n]

@Suppress("NOTHING_TO_INLINE")
public inline operator fun <T> ArrayLike<T>.set(n: Number, value: T) {
    asDynamic()[n] = value
}
