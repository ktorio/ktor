/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fetch

typealias ArrayBufferLike = Any

external interface ArrayBufferView {
    var buffer: ArrayBufferLike
    var byteLength: Number
    var byteOffset: Number
}

external interface ArrayBuffer {
    var byteLength: Number
    fun slice(begin: Number, end: Number? = definedExternally): ArrayBuffer

//    companion object : ArrayBufferConstructor by definedExternally
}


external interface ArrayBufferConstructor {
    var prototype: ArrayBuffer
    fun isView(arg: Any): Boolean
}


external interface Uint8Array {
    var BYTES_PER_ELEMENT: Number
    var buffer: ArrayBufferLike
    var byteLength: Number
    var byteOffset: Number
    fun copyWithin(target: Number, start: Number, end: Number? = definedExternally): Uint8Array /* this */
    fun every(callbackfn: (value: Number, index: Number, array: Uint8Array) -> Any, thisArg: Any? = definedExternally): Boolean
    fun fill(value: Number, start: Number? = definedExternally, end: Number? = definedExternally): Uint8Array /* this */
    fun filter(callbackfn: (value: Number, index: Number, array: Uint8Array) -> Any, thisArg: Any? = definedExternally): Uint8Array
    fun find(predicate: (value: Number, index: Number, obj: Uint8Array) -> Boolean, thisArg: Any? = definedExternally): Number?
    fun findIndex(predicate: (value: Number, index: Number, obj: Uint8Array) -> Boolean, thisArg: Any? = definedExternally): Number
    fun forEach(callbackfn: (value: Number, index: Number, array: Uint8Array) -> Unit, thisArg: Any? = definedExternally)
    fun indexOf(searchElement: Number, fromIndex: Number? = definedExternally): Number
    fun join(separator: String? = definedExternally): String
    fun lastIndexOf(searchElement: Number, fromIndex: Number? = definedExternally): Number
    var length: Number
    fun map(callbackfn: (value: Number, index: Number, array: Uint8Array) -> Number, thisArg: Any? = definedExternally): Uint8Array
    fun reduce(callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number): Number
    fun reduce(callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number, initialValue: Number): Number
    fun <U> reduce(callbackfn: (previousValue: U, currentValue: Number, currentIndex: Number, array: Uint8Array) -> U, initialValue: U): U
    fun reduceRight(callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number): Number
    fun reduceRight(callbackfn: (previousValue: Number, currentValue: Number, currentIndex: Number, array: Uint8Array) -> Number, initialValue: Number): Number
    fun <U> reduceRight(callbackfn: (previousValue: U, currentValue: Number, currentIndex: Number, array: Uint8Array) -> U, initialValue: U): U
    fun reverse(): Uint8Array
    fun set(array: ArrayLike<Number>, offset: Number? = definedExternally)
    fun slice(start: Number? = definedExternally, end: Number? = definedExternally): Uint8Array
    fun some(callbackfn: (value: Number, index: Number, array: Uint8Array) -> Any, thisArg: Any? = definedExternally): Boolean
    fun sort(compareFn: ((a: Number, b: Number) -> Number)? = definedExternally): Uint8Array /* this */
    fun subarray(begin: Number? = definedExternally, end: Number? = definedExternally): Uint8Array
    fun toLocaleString(): String
    override fun toString(): String
    @nativeGetter
    operator fun get(index: Number): Number?
    @nativeSetter
    operator fun set(index: Number, value: Number)

//    companion object : Uint8ArrayConstructor by definedExternally
}

external interface Uint8ArrayConstructor {
    var prototype: Uint8Array
    var BYTES_PER_ELEMENT: Number
    fun of(vararg items: Number): Uint8Array
    fun from(arrayLike: ArrayLike<Number>): Uint8Array
    fun <T> from(arrayLike: ArrayLike<T>, mapfn: (v: T, k: Number) -> Number, thisArg: Any? = definedExternally): Uint8Array
}

external interface ArrayLike<T> {
    var length: Number
    @nativeGetter
    operator fun get(n: Number): T?
    @nativeSetter
    operator fun set(n: Number, value: T)
}
