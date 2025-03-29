/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.peer

import org.w3c.dom.mediacapture.MediaDevices

public open external class DOMException {
    public var code: Number
    public var message: String
    public var name: String
    public var ABORT_ERR: Number
    public var DATA_CLONE_ERR: Number
    public var DOMSTRING_SIZE_ERR: Number
    public var HIERARCHY_REQUEST_ERR: Number
    public var INDEX_SIZE_ERR: Number
    public var INUSE_ATTRIBUTE_ERR: Number
    public var INVALID_ACCESS_ERR: Number
    public var INVALID_CHARACTER_ERR: Number
    public var INVALID_MODIFICATION_ERR: Number
    public var INVALID_NODE_TYPE_ERR: Number
    public var INVALID_STATE_ERR: Number
    public var NAMESPACE_ERR: Number
    public var NETWORK_ERR: Number
    public var NOT_FOUND_ERR: Number
    public var NOT_SUPPORTED_ERR: Number
    public var NO_DATA_ALLOWED_ERR: Number
    public var NO_MODIFICATION_ALLOWED_ERR: Number
    public var QUOTA_EXCEEDED_ERR: Number
    public var SECURITY_ERR: Number
    public var SYNTAX_ERR: Number
    public var TIMEOUT_ERR: Number
    public var TYPE_MISMATCH_ERR: Number
    public var URL_MISMATCH_ERR: Number
    public var VALIDATION_ERR: Number
    public var WRONG_DOCUMENT_ERR: Number
}

public external interface ReadonlyMap<K> {
    public fun entries(): Iterator<dynamic>
    public fun keys(): Iterable<K>
    public fun values(): Iterator<dynamic>
    public fun forEach(
        callbackfn: (value: dynamic, key: K, map: ReadonlyMap<K>) -> Unit,
        thisArg: Any = definedExternally
    )

    public fun <V> get(key: K): V?
    public fun has(key: K): Boolean
    public var size: Number
}

public external interface Navigator {
    public val mediaDevices: MediaDevices
}

public external val navigator: Navigator
